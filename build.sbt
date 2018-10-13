val scalaTestVersion = "3.0.5"
val scalaMockSupportVersion = "3.6.0"
val akkaStreamVersion = "2.5.17"


lazy val commonSettings = Seq(
  organization := "com.github.kperson",

  version := "1.0.0",
  scalaVersion := "2.12.6",
  parallelExecution in Test := false
)

lazy val app = (project in file("app")).
  settings(commonSettings: _*).
  settings(
   fork in run := true,
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  ).
  settings(libraryDependencies ++= Seq (
    "com.amazonaws"           % "aws-lambda-java-events"       % "2.2.2",
    "com.amazonaws"           %  "aws-java-sdk-core"           % "1.11.416",
    "com.amazonaws"           % "aws-lambda-java-core"         % "1.2.0",
    "org.json4s"              %% "json4s-jackson"              % "3.6.1",
    "org.scala-stm"           %% "scala-stm"                   % "0.8",
    "org.asynchttpclient"     %  "async-http-client"           % "2.5.3",
    "org.scala-lang.modules"  %% "scala-xml"                   % "1.1.0",
    "ch.qos.logback"          %  "logback-classic"             % "1.2.3" % "runtime",
    "com.typesafe.akka"       %% "akka-stream"                 % akkaStreamVersion,
    "com.typesafe.akka"       %% "akka-http"                   % "10.1.5",
    "org.scalatest"           %% "scalatest"                   % scalaTestVersion         % "test",
    "org.scalamock"           %% "scalamock-scalatest-support" % scalaMockSupportVersion  % "test",
    "com.typesafe.akka"       %% "akka-stream-testkit"         % "2.5.16"                 % "test"
  ))