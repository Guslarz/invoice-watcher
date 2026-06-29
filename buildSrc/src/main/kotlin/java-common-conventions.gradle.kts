plugins {
  java
  id("java-code-analysis-conventions")
  id("dependencies.lombok-conventions")
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

repositories {
  mavenCentral()
}