enablePlugins(JavaAppPackaging)

lazy val root = (project in file(".")).
  configs(IntegrationTest)
  .settings(
    inThisBuild(List(
      organization := "com.pagantis",
      scalaVersion := "2.12.7"
    )),
    name := "batch-http",
    Defaults.itSettings
  )

trapExit := false

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.4",
  "com.iheart" %% "ficus" % "1.4.5",
  "com.typesafe.akka" %% "akka-slf4j" % "2.5.21",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-stream" % "2.5.21",
  "com.typesafe.akka" %% "akka-http" % "10.1.5",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.21" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % "it,test"
)

bashScriptExtraDefines += s"""APP_VERSION=${version.value}"""


import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runClean,                               // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  setNextVersion,                         // : ReleaseStep
  commitNextVersion                       // : ReleaseStep
)
