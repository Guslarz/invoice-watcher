plugins {
  java
  id("java-code-analysis-conventions")
  id("dependencies.lombok-conventions")
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

repositories {
  mavenCentral()
}