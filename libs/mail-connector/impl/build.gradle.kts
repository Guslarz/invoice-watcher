plugins {
  id("spring.boot-lib-conventions")
}

description = "IMAP mailbox connection based on Jakarta Mail, scanning folders by UID."

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.jakarta.mail)
}
