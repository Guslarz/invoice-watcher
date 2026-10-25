package com.guslarz.invoicewatcher.mailconnector.impl;

import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionException;
import com.guslarz.invoicewatcher.mailconnector.api.MailConnectionProperties;
import com.guslarz.invoicewatcher.mailconnector.api.mailcredentials.BasicAuthMailCredentials;
import com.guslarz.invoicewatcher.secret.api.Secret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Connects to local servers that complete the TLS handshake and then never answer. Their
 * certificates in {@code src/test/resources/tls} are self-signed and trusted by the test JVM (see
 * the module build file). They were generated with:
 *
 * <pre>
 * keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=$HOST" \
 *     -ext "SAN=dns:$HOST" -keystore $HOST.p12 -storetype PKCS12 -storepass changeit
 * keytool -exportcert -alias server -keystore $HOST.p12 -storepass changeit -file $HOST.crt
 * keytool -importcert -alias $HOST -file $HOST.crt -keystore truststore.p12 -storetype PKCS12 \
 *     -storepass changeit -noprompt
 * </pre>
 */
class MailConnectionFactoryImplTest {

  private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();
  private static final Duration TIMEOUT = Duration.ofSeconds(1);
  private static final Duration TEST_LIMIT = Duration.ofSeconds(10);

  private final MailConnectionFactoryImpl uut =
      new MailConnectionFactoryImpl(new MailConnectorImapProperties(TIMEOUT, TIMEOUT));
  private final List<Socket> acceptedSockets = new ArrayList<>();
  private ServerSocket server;

  @AfterEach
  void closeServer() throws IOException {
    for (Socket socket : acceptedSockets) {
      socket.close();
    }
    server.close();
  }

  @Test
  @DisplayName("MAIL-010: rejects a trusted certificate issued for another host")
  void rejectsCertificateForAnotherHost() throws Exception {
    // given
    server = silentServer(tlsServerSocket("other.example"));

    // when
    Throwable thrown = assertTimeoutPreemptively(TEST_LIMIT, () -> catchThrowable(this::connect));

    // then
    assertThat(thrown).isInstanceOf(MailConnectionException.class);
    assertThat(causes(thrown))
        .anySatisfy(cause -> assertThat(cause)
            .isInstanceOf(CertificateException.class)
            .hasMessageContaining("localhost"));
  }

  @Test
  @DisplayName("MAIL-010: accepts a trusted certificate issued for the configured host")
  void acceptsCertificateForConfiguredHost() throws Exception {
    // given
    server = silentServer(tlsServerSocket("localhost"));

    // when
    Throwable thrown = assertTimeoutPreemptively(TEST_LIMIT, () -> catchThrowable(this::connect));

    // then the connection gets past the TLS handshake and only times out waiting for the greeting
    assertThat(thrown).isInstanceOf(MailConnectionException.class);
    assertThat(causes(thrown))
        .noneSatisfy(cause -> assertThat(cause).isInstanceOf(SSLException.class));
  }

  @Test
  @DisplayName("MAIL-013: gives up on a server that accepts the connection and never answers")
  void givesUpOnServerThatNeverAnswers() throws Exception {
    // given
    server = silentServer(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));

    // when
    Throwable thrown = assertTimeoutPreemptively(TEST_LIMIT, () -> catchThrowable(this::connect));

    // then
    assertThat(thrown).isInstanceOf(MailConnectionException.class);
  }

  private void connect() {
    Secret password = mock(Secret.class);
    when(password.getDecrypted()).thenReturn("password");
    uut.createConnection(MailConnectionProperties.builder()
        .host("localhost")
        .port(server.getLocalPort())
        .mailCredentials(BasicAuthMailCredentials.builder()
            .username("user")
            .password(password)
            .build())
        .build());
  }

  private static List<Throwable> causes(Throwable thrown) {
    return Stream.iterate(thrown, cause -> cause != null, Throwable::getCause).toList();
  }

  private ServerSocket silentServer(ServerSocket serverSocket) {
    Thread.ofVirtual().start(() -> {
      try {
        while (!serverSocket.isClosed()) {
          Socket socket = serverSocket.accept();
          synchronized (acceptedSockets) {
            acceptedSockets.add(socket);
          }
          // A TLS server answers the handshake on the first read; the read itself never returns.
          Thread.ofVirtual().start(() -> {
            try {
              socket.getInputStream().read();
            } catch (IOException ignored) {
              // closed by the test or the client
            }
          });
        }
      } catch (IOException ignored) {
        // server closed after the test
      }
    });
    return serverSocket;
  }

  private static SSLServerSocket tlsServerSocket(String host) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream keyStoreFile =
             MailConnectionFactoryImplTest.class.getResourceAsStream("/tls/" + host + ".p12")) {
      keyStore.load(keyStoreFile, KEYSTORE_PASSWORD);
    }
    KeyManagerFactory keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(keyStore, KEYSTORE_PASSWORD);
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagers.getKeyManagers(), null, null);
    return (SSLServerSocket) sslContext.getServerSocketFactory()
        .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
  }
}
