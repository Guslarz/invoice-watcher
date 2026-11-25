plugins {
  id("jvm.test-fixtures-conventions")
}

description = "Test-only container image pins and isolated Kafka and Datastore fixtures."

dependencies {
  testFixturesApi(libs.testcontainers.kafka)
  testFixturesApi(libs.testcontainers.gcloud)
}
