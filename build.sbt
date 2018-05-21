import sbt.Keys.scalacOptions

name := "kafka-flink-cassandra"

version := "0.1"


val commonDependencies = {
  Seq()
}

lazy val projectSettings = Seq(
  scalaVersion := "2.11.12",
  fork in Test := true,
  scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")
)

def baseProject(name: String): Project = (
  Project(name, file(name))
    settings (projectSettings: _*)
  )

lazy val root = (
  project.in(file("."))
    aggregate(websocketServer, websocketClientKafka)
  )

lazy val websocketServer = (
  baseProject("websocket-server")
    settings(
    libraryDependencies ++=
      commonDependencies ++ Seq(
        "com.typesafe" % "config" % "1.3.2",
        "com.typesafe.akka" %% "akka-stream" % "2.5.12",
        "com.typesafe.akka" %% "akka-http" % "10.1.1",
        "com.typesafe.akka" %% "akka-http-core" % "10.1.1",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1",
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
      ),
    mainClass in Compile := Some("WebsocketServer")
  ))

lazy val websocketClientKafka = (
  baseProject("websocket-client-kafka")
    settings (
    libraryDependencies ++=
      commonDependencies ++ Seq(
        "com.typesafe" % "config" % "1.3.2",
        "com.typesafe.akka" %% "akka-stream" % "2.5.12",
        "com.typesafe.akka" %% "akka-http" % "10.1.1",
        "com.typesafe.akka" %% "akka-http-core" % "10.1.1",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.1",
        "com.typesafe.akka" %% "akka-stream-kafka" % "0.20",
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
      ),
    mainClass in Compile := Some("WebsocketClientToKafka")
  ))

lazy val flinkProcessor = (
  baseProject("flink-processor")
    settings (
    libraryDependencies ++=
      commonDependencies ++ Seq(
        "com.typesafe" % "config" % "1.3.2",
        "org.apache.flink" %% "flink-scala" % "1.4.2",
        "org.apache.flink" %% "flink-streaming-scala" % "1.4.2",
        "org.apache.flink" %% "flink-connector-kafka-0.11" % "1.4.2",
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
        "ch.qos.logback" % "logback-classic" % "1.2.3",
      ),
    mainClass in Compile := Some("FlinkProcessTopic")
  ))

