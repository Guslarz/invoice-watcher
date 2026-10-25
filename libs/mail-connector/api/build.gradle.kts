plugins {
  id("springboot.lib-conventions")
}

description = "Mailbox connection interfaces: connections, queries, messages, credentials."

dependencies {
  api(projects.libs.secretApi)
}
