package jvm

plugins { java }

val mainSources = the<JavaPluginExtension>().sourceSets.named("main")
tasks.register<JavaExec>("localH2Server") {
  group = "application"
  description = "Starts a loopback H2 TCP server for shared disposable local data."
  classpath = mainSources.get().runtimeClasspath
  mainClass.set("org.h2.tools.Server")
  args("-tcp", "-tcpPort", "9093", "-ifNotExists", "-baseDir", layout.buildDirectory.dir("local-h2").get().asFile)
}
tasks.register<JavaExec>("localH2Initialize") {
  group = "application"
  description = "Initializes an empty disposable local H2 database; refuses existing tables."
  classpath = mainSources.get().runtimeClasspath
  mainClass.set("com.guslarz.invoicewatcher.dao.userconfiguration.jpa.LocalH2SchemaInitializer")
  args("jdbc:h2:tcp://localhost:9093/./invoice-watcher")
}
