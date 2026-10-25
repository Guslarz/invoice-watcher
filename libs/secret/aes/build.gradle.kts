plugins {
  id("springboot.lib-conventions")
}

description = "AES-GCM implementation of secrets, decrypted only when used."

dependencies {
  api(projects.libs.secretApi)
  implementation(projects.libs.utilAes)

  implementation(libs.spring.boot.autoconfigure)
  implementation(libs.spring.boot.starter.validation)

  implementation(libs.jspecify)
}
