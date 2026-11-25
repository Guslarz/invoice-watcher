package com.guslarz.invoicewatcher.attachmentstorage.filesystem.service;

import com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment;
import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.attachmentstorage.filesystem.properties.FilesystemStorageProperties;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/** One process owns a private root; complete files are published without replacement. */
@Slf4j
public final class FilesystemAttachmentStorage implements AttachmentStorage, AutoCloseable {
  private static final int MAGIC = 0x49574131;
  private static final int MAX_HEADER = 4096;
  private static final int MAX_BYTES = 16777216;
  private static final Pattern KEY = Pattern.compile("[a-f0-9]{64}/[a-f0-9]{64}");
  private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
  private static final Pattern GENERATION =
      Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
  private static final Pattern STAGING = Pattern.compile("\\.(staging|probe)-[a-f0-9-]{36}\\.tmp");
  private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
      PosixFilePermissions.fromString("rwx------");
  private static final Set<PosixFilePermission> FILE_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");

  private final FilesystemStorageProperties properties;
  private final Clock clock;
  private final Publication publication;
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final Path root;
  private final FileChannel lockChannel;
  private final FileLock processLock;
  private final ScheduledExecutorService janitor;
  private boolean closed;

  public FilesystemAttachmentStorage(FilesystemStorageProperties properties, Clock clock) {
    this(properties, clock, (target, staged) -> Files.createLink(target, staged));
  }

  FilesystemAttachmentStorage(
      FilesystemStorageProperties properties, Clock clock, Publication publication) {
    this.properties = properties;
    this.clock = clock;
    this.publication = publication;
    if (properties.directory() == null
        || !properties.directory().isAbsolute()
        || properties.cleanupInterval() == null
        || properties.cleanupInterval().toMillis() < 1000) {
      throw invalidDirectory();
    }
    OpenRoot opened = open(properties.directory());
    root = opened.path();
    lockChannel = opened.channel();
    processLock = opened.lock();
    janitor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("attachment-file-cleanup").factory());
    cleanupExpired();
    janitor.scheduleWithFixedDelay(
        this::cleanupExpired,
        properties.cleanupInterval().toMillis(),
        properties.cleanupInterval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @Override
  public String storeId() {
    return properties.storeId();
  }

  @Override
  public synchronized StoredAttachment put(
      String key,
      String ownerScope,
      byte[] bytes,
      String sha256,
      Instant capturedAt,
      Instant availableUntil) {
    requireOpen();
    validKey(key);
    byte[] snapshot = bytes.clone();
    if (!key.startsWith(ownerScope + "/")
        || snapshot.length > MAX_BYTES
        || capturedAt == null
        || availableUntil == null
        || !availableUntil.isAfter(capturedAt)
        || !digest(snapshot).equals(sha256)) {
      throw failure("evidence-integrity-failure");
    }
    if (!availableUntil.isAfter(clock.instant())) {
      throw failure("evidence-expired");
    }
    Path staged = null;
    try {
      Path directory = root.resolve(key.substring(0, 64));
      privateDirectory(directory);
      Path target = directory.resolve(key.substring(65) + ".blob");
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        return existing(target, ownerScope, snapshot, sha256, capturedAt, availableUntil);
      }
      Metadata metadata =
          new Metadata(
              1,
              UUID.randomUUID().toString(),
              ownerScope,
              sha256,
              snapshot.length,
              capturedAt,
              availableUntil);
      byte[] header = mapper.writeValueAsBytes(metadata);
      if (header.length > MAX_HEADER) {
        throw failure("evidence-integrity-failure");
      }
      staged = directory.resolve(".staging-" + UUID.randomUUID() + ".tmp");
      try (FileChannel file =
              FileChannel.open(
                  staged,
                  Set.of(
                      StandardOpenOption.CREATE_NEW,
                      StandardOpenOption.WRITE,
                      LinkOption.NOFOLLOW_LINKS),
                  PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
          DataOutputStream output = new DataOutputStream(Channels.newOutputStream(file))) {
        output.writeInt(MAGIC);
        output.writeInt(header.length);
        output.write(header);
        output.write(snapshot);
        output.flush();
        file.force(true);
      }
      try {
        publication.publish(target, staged);
        forceDirectory(directory);
        forceDirectory(root);
      } catch (FileAlreadyExistsException conflict) {
        return existing(target, ownerScope, snapshot, sha256, capturedAt, availableUntil);
      }
      return new StoredAttachment(metadata.generation(), sha256, snapshot.length);
    } catch (IOException exception) {
      throw failure("attachment-storage-unavailable");
    } finally {
      if (staged != null) {
        try {
          Files.deleteIfExists(staged);
        } catch (IOException ignored) {
          // A future startup/cleanup owns abandoned application staging names.
        }
      }
    }
  }

  @Override
  public synchronized byte[] read(String key, String generation, long maxBytes) {
    requireOpen();
    validKey(key);
    if (generation == null
        || !GENERATION.matcher(generation).matches()
        || maxBytes < 0
        || maxBytes > MAX_BYTES) {
      throw failure("invalid-evidence-reference");
    }
    Path directory = root.resolve(key.substring(0, 64));
    Path target = directory.resolve(key.substring(65) + ".blob");
    try {
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("evidence-missing");
      }
      privateDirectory(directory);
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        throw failure("evidence-missing");
      }
      Metadata metadata = metadata(target);
      if (!metadata.generation().equals(generation)) {
        throw failure("evidence-missing");
      }
      if (!metadata.availableUntil().isAfter(clock.instant())) {
        throw failure("evidence-expired");
      }
      if (!metadata.ownerScope().equals(key.substring(0, 64)) || metadata.byteLength() > maxBytes) {
        throw failure("evidence-integrity-failure");
      }
      return content(target, metadata);
    } catch (NoSuchFileException exception) {
      throw failure("evidence-missing");
    } catch (IOException exception) {
      throw failure("attachment-storage-unavailable");
    }
  }

  private StoredAttachment existing(
      Path target, String owner, byte[] bytes, String sha, Instant capturedAt, Instant until)
      throws IOException {
    Metadata metadata = metadata(target);
    if (!metadata.ownerScope().equals(owner)
        || !metadata.sha256().equals(sha)
        || metadata.byteLength() != bytes.length
        || !metadata.capturedAt().equals(capturedAt)
        || !metadata.availableUntil().equals(until)
        || !Arrays.equals(content(target, metadata), bytes)) {
      throw failure("evidence-integrity-failure");
    }
    return new StoredAttachment(metadata.generation(), sha, bytes.length);
  }

  private Metadata metadata(Path file) throws IOException {
    if (Files.isSymbolicLink(file)
        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
        || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
            .equals(FILE_PERMISSIONS)) {
      throw failure("invalid-evidence-reference");
    }
    long size = Files.size(file);
    if (size < 8 || size > MAX_HEADER + MAX_BYTES + 8L) {
      throw failure("evidence-integrity-failure");
    }
    try (DataInputStream input =
        new DataInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))) {
      if (input.readInt() != MAGIC) {
        throw failure("evidence-integrity-failure");
      }
      int length = input.readInt();
      if (length < 1 || length > MAX_HEADER) {
        throw failure("evidence-integrity-failure");
      }
      byte[] header = input.readNBytes(length);
      Metadata result;
      try {
        result = mapper.readValue(header, Metadata.class);
      } catch (RuntimeException exception) {
        throw failure("evidence-integrity-failure");
      }
      if (header.length != length
          || result == null
          || result.version() != 1
          || result.generation() == null
          || !GENERATION.matcher(result.generation()).matches()
          || result.ownerScope() == null
          || !HASH.matcher(result.ownerScope()).matches()
          || result.sha256() == null
          || !HASH.matcher(result.sha256()).matches()
          || result.byteLength() < 0
          || result.byteLength() > MAX_BYTES
          || size != 8L + length + result.byteLength()
          || result.capturedAt() == null
          || result.availableUntil() == null
          || !result.availableUntil().isAfter(result.capturedAt())) {
        throw failure("evidence-integrity-failure");
      }
      return result;
    }
  }

  private byte[] content(Path file, Metadata metadata) throws IOException {
    try (DataInputStream input =
        new DataInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))) {
      if (input.readInt() != MAGIC) {
        throw failure("evidence-integrity-failure");
      }
      int length = input.readInt();
      if (length < 1 || length > MAX_HEADER) {
        throw failure("evidence-integrity-failure");
      }
      input.skipNBytes(length);
      byte[] bytes = input.readNBytes(metadata.byteLength() + 1);
      if (bytes.length != metadata.byteLength() || !digest(bytes).equals(metadata.sha256())) {
        throw failure("evidence-integrity-failure");
      }
      return bytes;
    }
  }

  public synchronized void cleanupExpired() {
    if (closed) {
      return;
    }
    try (var entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        try {
          cleanEntry(entry);
        } catch (IOException | AttachmentException exception) {
          log.warn("Attachment filesystem entry cleanup failed; retrying on the next run");
        }
      }
    } catch (IOException | AttachmentException exception) {
      log.warn("Attachment filesystem cleanup failed; retrying on the next scheduled run");
    }
  }

  private void cleanEntry(Path entry) throws IOException {
    if (Files.isSymbolicLink(entry)) {
      return;
    }
    String name = Objects.requireNonNull(entry.getFileName()).toString();
    if (STAGING.matcher(name).matches() && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
      Files.deleteIfExists(entry);
    } else if (HASH.matcher(name).matches()
        && Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
      cleanOwner(entry);
    }
  }

  private void cleanOwner(Path directory) throws IOException {
    privateDirectory(directory);
    try (var files = Files.newDirectoryStream(directory)) {
      for (Path file : files) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
          continue;
        }
        String name = Objects.requireNonNull(file.getFileName()).toString();
        if (STAGING.matcher(name).matches()) {
          Files.deleteIfExists(file);
        } else if (name.endsWith(".blob")
            && HASH.matcher(name.substring(0, name.length() - 5)).matches()) {
          try {
            Metadata metadata = metadata(file);
            if (metadata
                    .ownerScope()
                    .equals(Objects.requireNonNull(directory.getFileName()).toString())
                && !metadata.availableUntil().isAfter(clock.instant())) {
              Files.delete(file);
            }
          } catch (AttachmentException exception) {
            // Corrupt/unknown entries are not a license to delete unrelated data.
          }
        }
      }
    }
  }

  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      janitor.shutdownNow();
      try {
        processLock.release();
        lockChannel.close();
      } catch (IOException exception) {
        throw failure("attachment-storage-unavailable");
      }
    }
  }

  private void requireOpen() {
    if (closed) {
      throw failure("attachment-storage-unavailable");
    }
  }

  private static OpenRoot open(Path directory) {
    FileChannel channel = null;
    FileLock lock = null;
    try {
      privateDirectory(directory);
      Path root = directory.toRealPath();
      channel =
          FileChannel.open(
              root.resolve(".lock"),
              Set.of(
                  StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
              PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
      lock = channel.tryLock();
      if (lock == null) {
        throw invalidDirectory();
      }
      probe(root);
      return new OpenRoot(root, channel, lock);
    } catch (IOException | RuntimeException exception) {
      try {
        if (lock != null) {
          lock.release();
        }
        if (channel != null) {
          channel.close();
        }
      } catch (IOException ignored) {
        // Preserve a sanitized startup diagnostic.
      }
      throw invalidDirectory();
    }
  }

  private static void probe(Path root) throws IOException {
    Path stage = root.resolve(".staging-" + UUID.randomUUID() + ".tmp");
    Path link = root.resolve(".probe-" + UUID.randomUUID() + ".tmp");
    try {
      Files.createFile(stage, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
      Files.createLink(link, stage);
      forceDirectory(root);
    } finally {
      Files.deleteIfExists(link);
      Files.deleteIfExists(stage);
    }
  }

  private static void privateDirectory(Path directory) throws IOException {
    if (Files.isSymbolicLink(directory)) {
      throw new IOException("Unsafe storage directory");
    }
    Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
        || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
            .equals(DIRECTORY_PERMISSIONS)) {
      throw new IOException("Storage directory must be private");
    }
  }

  private static void forceDirectory(Path directory) throws IOException {
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private static void validKey(String key) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw failure("invalid-evidence-reference");
    }
  }

  private static String digest(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static AttachmentException failure(String reason) {
    return new AttachmentException(reason);
  }

  private static IllegalArgumentException invalidDirectory() {
    return new IllegalArgumentException(
        "Invalid invoice-watcher.attachment-storage.filesystem.directory: requires a private"
            + " exclusive POSIX local directory with atomic publication");
  }

  private record OpenRoot(Path path, FileChannel channel, FileLock lock) {}

  private record Metadata(
      int version,
      String generation,
      String ownerScope,
      String sha256,
      int byteLength,
      Instant capturedAt,
      Instant availableUntil) {}

  @FunctionalInterface
  interface Publication {
    void publish(Path target, Path staged) throws IOException;
  }
}
