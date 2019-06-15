val scalaTestVersion = "3.0.5"
val scalaMockSupportVersion = "3.6.0"
val akkaStreamVersion = "2.5.19"

lazy val commonSettings = Seq(
  organization := "com.github.kperson",
  version := "1.0.0",
  scalaVersion := "2.12.8",
  parallelExecution in Test := false,
  fork := true,
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList("reference.conf") => MergeStrategy.concat
    case _ => MergeStrategy.first
  }
)

lazy val awsClient = (project in file("aws-client")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Seq (
    "com.amazonaws"           %  "aws-java-sdk-core"           % "1.11.416",
    "com.amazonaws"           %  "aws-lambda-java-core"        % "1.2.0",
    "com.typesafe.play"       %% "play-json"                   % "2.7.3",
    "org.scala-stm"           %% "scala-stm"                   % "0.8"
  ))

lazy val testSupport = (project in file("test-support")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Seq (
    "org.scalatest"           %% "scalatest"                   % scalaTestVersion,
    "org.scalamock"           %% "scalamock-scalatest-support" % scalaMockSupportVersion,
    "org.json4s"              %% "json4s-jackson"              % "3.6.1"
  )).dependsOn(awsClient)

lazy val app = (project in file("app")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Seq (
    "com.typesafe.play"       %% "play-json"                   % "2.7.3",
    "tech.sparse"             %% "trail"                       % "0.2.0",
    "commons-logging"         % "commons-logging"              % "1.1.1"
  )).dependsOn(testSupport % "test", awsClient)
