plugins {
  id("spring.boot-lib-conventions")
}

description = "IMAP mailbox connection based on Angus Mail, scanning folders by UID."

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.angus.mail)
}

// The test client trusts the certificates of the test servers (src/test/resources/tls).
tasks.test {
  systemProperty("javax.net.ssl.trustStore", file("src/test/resources/tls/truststore.p12").absolutePath)
  systemProperty("javax.net.ssl.trustStorePassword", "changeit")
}
