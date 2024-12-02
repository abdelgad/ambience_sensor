ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "ambience_sensor"
  )

// akka
resolvers += "Akka library repository".at("https://repo.akka.io/maven")
val AkkaVersion = "2.10.0"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion
)

// logger
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.7" // Compatible with SLF4J

// spray-json
libraryDependencies += "io.spray" %% "spray-json" % "1.3.6"

// JavaCV (webcam)
libraryDependencies += "org.bytedeco" % "javacv-platform" % "1.5.11"

// https://github.com/haifengl/smile
libraryDependencies += "com.github.haifengl" %% "smile-scala" % "3.1.1"

// phidget22
unmanagedJars in Compile += file("lib/phidget22.jar")

fork := true