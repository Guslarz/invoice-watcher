plugins {
  id("springboot.lib-conventions")
}

description = "Event producer that sends events to Kafka."

dependencies {
  api(projects.libs.eventProducerApi)

  implementation(libs.spring.boot.starter)
  implementation(libs.spring.boot.starter.validation)
  implementation(libs.spring.kafka)

  // JacksonJsonSerializer needs Jackson on the compile classpath; spring-kafka makes it optional.
  implementation(libs.jackson.databind)
}
