package com.guslarz.invoicewatcher.mailattachment.impl.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.guslarz.invoicewatcher.attachmentstorage.api.model.StoredAttachment;
import com.guslarz.invoicewatcher.attachmentstorage.api.service.AttachmentStorage;
import com.guslarz.invoicewatcher.dao.mailattachment.api.model.AttachmentCapture;
import com.guslarz.invoicewatcher.dao.mailattachment.api.service.AttachmentCaptureDao;
import com.guslarz.invoicewatcher.mailattachment.api.exception.AttachmentException;
import com.guslarz.invoicewatcher.mailattachment.api.model.AttachmentManifest;
import com.guslarz.invoicewatcher.mailattachment.api.properties.MailAttachmentProperties;
import com.guslarz.invoicewatcher.mailattachment.api.service.AttachmentPart;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultMailAttachmentServiceTest {
  private static final String SOURCE = "imap-v1:" + "a".repeat(64);
  private final MutableClock clock = new MutableClock();
  private final MemoryStorage storage = new MemoryStorage();
  private final MemoryDao dao = new MemoryDao();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final DefaultMailAttachmentService uut = service(defaults());

  @Test
  @DisplayName(
      "ATTACHMENT-009 ATTACHMENT-010: explicit deletion differs from missing objects and suppresses"
          + " recapture")
  void distinguishesDeletedEvidenceFromUnexpectedlyMissingBytes() {
    // given
    var manifest = uut.capture("alice", SOURCE, pdf("original"));
    storage.missingRead = true;
    // when
    final var missing =
        catchThrowable(() -> uut.read("alice", SOURCE, manifest.attachments().getFirst()));
    var captured = dao.currentRecord();
    dao.markDeleted(captured.id(), captured.ownerScope(), clock.instant());
    var deleted =
        catchThrowable(() -> uut.read("alice", SOURCE, manifest.attachments().getFirst()));
    var recaptured = catchThrowable(() -> uut.capture("alice", SOURCE, pdf("original")));
    // then
    assertThat(missing).hasMessage("evidence-missing");
    assertThat(deleted).hasMessage("evidence-deleted");
    assertThat(recaptured).hasMessage("evidence-deleted");
    assertThat(storage.creates.get()).isEqualTo(1);
    assertThat(dao.currentRecord().manifest()).isNull();
  }

  @Test
  @DisplayName(
      "ATTACHMENT-001 ATTACHMENT-002: multipart attachments are explicit unsupported candidates")
  void excludesDescendantsOfUnsupportedMultipartAttachment() {
    // given
    var root = new TestPart(false, true, "multipart/mixed", "", List.of(pdf("nested")));
    // when
    var result = uut.capture("alice", SOURCE, root);
    // then
    assertThat(result.complete()).isTrue();
    assertThat(result.attachments()).hasSize(1);
    assertThat(result.attachments().getFirst().outcome()).isEqualTo("unsupported-type");
    assertThat(storage.objects).isEmpty();
  }

  @Test
  @DisplayName(
      "ATTACHMENT-001 ATTACHMENT-002 MAIL-033: captures PDFs in MIME order without parsing")
  void capturesUntrustedPdfAndExcludesAttachedMessageAndUnnamedInline() {
    // given
    var root =
        container(
            pdf("one"),
            container(
                pdf("two"),
                new TestPart(true, true, "message/rfc822", "", List.of(pdf("ignored")))),
            new TestPart(false, false, "application/pdf", "inline", List.of()),
            new TestPart(false, true, "text/plain", "unsupported", List.of()));
    // when
    var result = uut.capture("alice", SOURCE, root);
    // then
    assertThat(result.complete()).isTrue();
    assertThat(result.attachments())
        .extracting(item -> item.partPath())
        .containsExactly("1.1", "1.2.1", "1.4");
    assertThat(result.attachments())
        .extracting(item -> item.outcome())
        .containsExactly("stored", "stored", "unsupported-type");
    assertThat(storage.objects).hasSize(2);
  }

  @Test
  @DisplayName(
      "ATTACHMENT-004 EVENT-009: duplicates reuse immutable identity, generation and expiry")
  void reusesCapturedManifestAcrossRetryAndRestart() {
    // given
    var first = uut.capture("alice", SOURCE, pdf("one"));
    clock.advance(Duration.ofHours(1));
    // when
    var repeated = service(defaults()).capture("alice", SOURCE, pdf("one"));
    // then
    assertThat(repeated).isEqualTo(first);
    assertThat(storage.creates.get()).isEqualTo(1);
    assertThat(repeated.attachments().getFirst().evidence().objectKey())
        .doesNotContain("alice", SOURCE);
  }

  @Test
  @DisplayName("ATTACHMENT-004 ATTACHMENT-009: identical bytes remain scoped to application users")
  void isolatesUserOwnershipEvenWhenBytesAreIdentical() {
    // given
    var alice = uut.capture("alice", SOURCE, pdf("same"));
    var bob = uut.capture("bob", SOURCE, pdf("same"));
    // when
    var denied = catchThrowable(() -> uut.read("bob", SOURCE, alice.attachments().getFirst()));
    var bytes = uut.read("alice", SOURCE, alice.attachments().getFirst());
    // then
    assertThat(storage.objects).hasSize(2);
    assertThat(alice.attachments().getFirst().attachmentId())
        .isNotEqualTo(bob.attachments().getFirst().attachmentId());
    assertThat(denied).hasMessage("evidence-unauthorized");
    assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("same");
  }

  @Test
  @DisplayName("ATTACHMENT-003: rejects oversized bytes despite absent or misleading MIME sizes")
  void boundsDecodedBytesAndCandidateCount() {
    // given
    var bounded =
        service(
            new MailAttachmentProperties(
                3,
                6,
                2,
                3,
                1024,
                Duration.ofSeconds(5),
                Duration.ofDays(9),
                Duration.ofDays(7),
                Duration.ofDays(1),
                Duration.ofSeconds(30)));
    // when
    var result = bounded.capture("alice", SOURCE, container(pdf("123"), pdf("1234"), pdf("x")));
    // then
    assertThat(result.complete()).isFalse();
    assertThat(result.limitReason()).isEqualTo("total-byte-limit");
    assertThat(result.attachments())
        .extracting(item -> item.outcome())
        .containsExactly("stored", "size-limit");
    assertThat(storage.objects).hasSize(1);
  }

  @Test
  @DisplayName("ATTACHMENT-003: excessive MIME structure is explicitly incomplete")
  void boundsNestedMimeAndEnumeration() {
    // given
    var bounded =
        service(
            new MailAttachmentProperties(
                10,
                20,
                1,
                2,
                1024,
                Duration.ofSeconds(5),
                Duration.ofDays(9),
                Duration.ofDays(7),
                Duration.ofDays(1),
                Duration.ofSeconds(30)));
    // when
    var result = bounded.capture("alice", SOURCE, container(container(pdf("x"))));
    // then
    assertThat(result.complete()).isFalse();
    assertThat(result.limitReason()).isEqualTo("mime-depth-limit");
    assertThat(result.attachments()).isEmpty();
  }

  @Test
  @DisplayName(
      "ATTACHMENT-005 ATTACHMENT-006: lost upload response reuses object after lease recovery")
  void recoversUploadBeforeManifestPersistence() {
    // given
    storage.loseResponse = true;
    // when
    var failed = catchThrowable(() -> uut.capture("alice", SOURCE, pdf("same")));
    clock.advance(Duration.ofMinutes(2));
    var recovered = service(defaults()).capture("alice", SOURCE, pdf("same"));
    // then
    assertThat(failed).hasMessage("attachment-storage-unavailable");
    assertThat(recovered.attachments().getFirst().evidence().generation()).isEqualTo("1");
    assertThat(storage.creates.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("ATTACHMENT-004 ATTACHMENT-006: conflicting bytes never overwrite a partial capture")
  void rejectsChangedBytesOnUploadRecovery() {
    // given
    storage.loseResponse = true;
    var failed = catchThrowable(() -> uut.capture("alice", SOURCE, pdf("first")));
    clock.advance(Duration.ofMinutes(2));
    // when
    var conflict = catchThrowable(() -> uut.capture("alice", SOURCE, pdf("second")));
    // then
    assertThat(failed).isInstanceOf(AttachmentException.class);
    assertThat(conflict).hasMessage("evidence-integrity-failure");
    assertThat(storage.creates.get()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "ATTACHMENT-007 ATTACHMENT-010 ATTACHMENT-011: expiry denies reads and prevents resurrection")
  void expiresUnprocessedEvidenceAndRetainsSuppressionMarker() {
    // given
    var result = uut.capture("alice", SOURCE, pdf("same"));
    clock.advance(Duration.ofDays(9));
    // when
    var expiredRead =
        catchThrowable(() -> uut.read("alice", SOURCE, result.attachments().getFirst()));
    uut.expireMetadata();
    var repeated = catchThrowable(() -> uut.capture("alice", SOURCE, pdf("same")));
    // then
    assertThat(expiredRead).hasMessage("evidence-expired");
    assertThat(repeated).hasMessage("evidence-expired");
    assertThat(dao.currentRecord().manifest()).isNull();
    assertThat(dao.currentRecord().expired()).isTrue();
    assertThat(
            meters.get("invoice.watcher.attachments").tag("outcome", "expired").counter().count())
        .isEqualTo(2);
    assertThat(meters.getMeters())
        .allSatisfy(meter ->
            assertThat(meter.getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactly("outcome"));
    assertThat(storage.creates.get()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "ATTACHMENT-008: insufficient remaining lifetime stops publication without extending expiry")
  void refusesStalePublication() {
    // given
    var result = uut.capture("alice", SOURCE, pdf("same"));
    clock.advance(Duration.ofDays(2));
    // when
    var failed = catchThrowable(() -> uut.requirePublicationWindow(result, Duration.ofDays(7)));
    // then
    assertThat(failed).hasMessage("insufficient-publication-window");
    assertThat(result.attachments().getFirst().evidence().availableUntil())
        .isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));
  }

  @Test
  @DisplayName(
      "ATTACHMENT-009 ATTACHMENT-011: integrity failures and source diagnostics expose no bytes")
  void rejectsCorruptionAndUnsafeSourceIdentity() {
    // given
    var result = uut.capture("alice", SOURCE, pdf("secret"));
    storage.corruptRead = true;
    // when
    var corrupt = catchThrowable(() -> uut.read("alice", SOURCE, result.attachments().getFirst()));
    var invalid = catchThrowable(() -> uut.capture("alice", "secret@example.com", pdf("secret")));
    // then
    assertThat(corrupt).hasMessage("evidence-integrity-failure").hasNoCause();
    assertThat(invalid).hasMessage("invalid-source-identity").hasNoCause();
  }

  @Test
  @DisplayName("ATTACHMENT-003: capture timeout cancels work without accepting a late manifest")
  void boundsSlowCapture() {
    // given
    var bounded =
        service(
            new MailAttachmentProperties(
                10,
                20,
                2,
                2,
                1024,
                Duration.ofMillis(50),
                Duration.ofDays(9),
                Duration.ofDays(7),
                Duration.ofDays(1),
                Duration.ofSeconds(30)));
    AttachmentPart slow =
        new TestPart(false, true, "application/pdf", "x", List.of()) {
          @Override
          public InputStream openStream() throws IOException {
            try {
              Thread.sleep(10000);
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              throw new IOException("private-content");
            }
            return super.openStream();
          }
        };
    // when
    var failed = catchThrowable(() -> bounded.capture("alice", SOURCE, slow));
    // then
    assertThat(failed).hasMessage("capture-time-limit").hasNoCause();
    assertThat(storage.creates.get()).isZero();
  }

  @Test
  @DisplayName("ATTACHMENT-004: identity components have unambiguous Unicode boundaries")
  void usesVersionedIdentityVectors() {
    // given
    String first = AttachmentIdentity.create("attachment-v1", "a:b", "c", "1.2");
    // when
    String repeated = AttachmentIdentity.create("attachment-v1", "a:b", "c", "1.2");
    String other = AttachmentIdentity.create("attachment-v1", "a", "b:c", "1.2");
    // then
    assertThat(first).isEqualTo(repeated).isNotEqualTo(other);
    assertThat(AttachmentIdentity.create("owner-v1", "żółć"))
        .isEqualTo("7363a76cadf3b5e79958c7b32a49caa0c1404e20c8da49399398ebd9cdb57e04");
  }

  private DefaultMailAttachmentService service(MailAttachmentProperties properties) {
    return new DefaultMailAttachmentService(properties, storage, dao, clock, meters);
  }

  private static MailAttachmentProperties defaults() {
    return new MailAttachmentProperties(
        10485760,
        26214400,
        20,
        20,
        65536,
        Duration.ofSeconds(60),
        Duration.ofDays(9),
        Duration.ofDays(7),
        Duration.ofDays(1),
        Duration.ofSeconds(30));
  }

  private static TestPart pdf(String bytes) {
    return new TestPart(false, true, "application/pdf", bytes, List.of());
  }

  private static TestPart container(AttachmentPart... parts) {
    return new TestPart(false, false, "multipart/mixed", "", List.of(parts));
  }

  private static class TestPart implements AttachmentPart {
    private final boolean attached;
    private final boolean candidate;
    private final String type;
    private final String bytes;
    private final List<AttachmentPart> children;

    TestPart(
        boolean attached,
        boolean candidate,
        String type,
        String bytes,
        List<AttachmentPart> children) {
      this.attached = attached;
      this.candidate = candidate;
      this.type = type;
      this.bytes = bytes;
      this.children = children;
    }

    public boolean attachedMessage() {
      return attached;
    }

    public boolean candidate() {
      return candidate;
    }

    public String mediaType() {
      return type;
    }

    public int childCount() {
      return children.size();
    }

    public AttachmentPart child(int index) {
      return children.get(index);
    }

    public InputStream openStream() throws IOException {
      return new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static class MemoryStorage implements AttachmentStorage {
    private final Map<String, byte[]> objects = new HashMap<>();
    private final AtomicInteger creates = new AtomicInteger();
    private boolean loseResponse;
    private boolean corruptRead;
    private boolean missingRead;

    public String storeId() {
      return "test";
    }

    public StoredAttachment put(
        String key, String owner, byte[] bytes, String digest, Instant captured, Instant expires) {
      byte[] previous = objects.putIfAbsent(key, bytes.clone());
      if (previous == null) {
        creates.incrementAndGet();
      }
      if (previous != null && !AttachmentIdentity.digest(previous).equals(digest)) {
        throw new AttachmentException("evidence-integrity-failure");
      }
      if (loseResponse) {
        loseResponse = false;
        throw new AttachmentException("attachment-storage-unavailable");
      }
      return new StoredAttachment("1", digest, bytes.length);
    }

    public byte[] read(String key, String generation, long maxBytes) {
      if (missingRead) {
        throw new AttachmentException("evidence-missing");
      }
      return corruptRead ? new byte[] {1} : objects.get(key).clone();
    }
  }

  private static class MemoryDao implements AttachmentCaptureDao {
    private AttachmentCapture record;
    private final Map<String, AttachmentCapture> records = new HashMap<>();

    public synchronized AttachmentCapture currentRecord() {
      return record;
    }

    public synchronized AttachmentCapture acquire(
        String id, String scope, String source, Instant now, Instant expires, Instant lease) {
      var old = records.get(id);
      if (old != null && old.deletedAt() != null) {
        throw new AttachmentException("evidence-deleted");
      }
      if (old != null && (!old.availableUntil().isAfter(now) || old.expired())) {
        throw new AttachmentException("evidence-expired");
      }
      if (old != null && old.manifest() != null) {
        return old;
      }
      if (old != null && old.leaseUntil().isAfter(now)) {
        throw new AttachmentException("capture-busy");
      }
      record =
          old == null
              ? new AttachmentCapture(
                  id, scope, source, 1, now, expires, 1, lease, false, null, null)
              : old.toBuilder().fence(old.fence() + 1).leaseUntil(lease).build();
      records.put(id, record);
      return record;
    }

    public synchronized AttachmentCapture find(String id) {
      return records.get(id);
    }

    public synchronized void complete(
        AttachmentCapture capture, AttachmentManifest manifest, Instant now) {
      var old = records.get(capture.id());
      if (old.fence() != capture.fence() || !old.leaseUntil().isAfter(now)) {
        throw new AttachmentException("stale-capture");
      }
      record = old.toBuilder().manifest(manifest).build();
      records.put(record.id(), record);
    }

    public synchronized void markDeleted(String id, String scope, Instant now) {
      var old = records.get(id);
      if (old == null || !old.ownerScope().equals(scope)) {
        throw new AttachmentException("evidence-unauthorized");
      }
      record = old.toBuilder().deletedAt(now).manifest(null).fence(old.fence() + 1).build();
      records.put(id, record);
    }

    public synchronized void expire(Instant now, int limit) {
      records.replaceAll((id, old) ->
          old.availableUntil().isAfter(now)
              ? old
              : old.toBuilder().expired(true).manifest(null).build());
      if (record != null) {
        record = records.get(record.id());
      }
    }
  }

  private static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-01T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }
}
