plugins {
  id("spring.boot-lib-conventions")
}

description = "IMAP mailbox connection based on Jakarta Mail, with date-based message search."

dependencies {
  api(projects.libs.mailConnectorApi)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.jakarta.mail)
}
