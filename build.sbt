val scalaTestVersion = "3.0.5"
val scalaMockSupportVersion = "3.6.0"
val akkaStreamVersion = "2.5.14"


lazy val commonSettings = Seq(
  organization := "com.github.kperson",

  version := "1.0.0",
  scalaVersion := "2.12.6",
  parallelExecution in Test := false
)

lazy val app = (project in file("app")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++= Seq (
    "com.amazonaws"            % "aws-lambda-java-events"      % "1.3.0",
    "com.amazonaws"            % "aws-lambda-java-core"        % "1.1.0",
    "com.amazonaws"            % "aws-java-sdk-dynamodb"       % "1.11.323",
    "org.json4s"              %% "json4s-jackson"              % "3.5.3",
    "org.scala-stm"           %% "scala-stm"                   % "0.8",
    "com.typesafe.akka"       %% "akka-stream"                 % akkaStreamVersion,
    "org.scalatest"           %% "scalatest"                   % scalaTestVersion         % "test",
    "org.scalamock"           %% "scalamock-scalatest-support" % scalaMockSupportVersion  % "test"
  ))