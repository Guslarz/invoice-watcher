plugins {
  id("springboot.lib-conventions")
}

description = "Durable application-user scoped attachment capture reservations."

dependencies {
  api(projects.libs.mailAttachmentApi)
  implementation(libs.spring.boot)
}
