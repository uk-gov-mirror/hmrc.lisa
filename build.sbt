import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}

val appName = "lisa"
name := "lisa"
PlayKeys.playDefaultPort := 8886
majorVersion := 1
retrieveManaged := true

lazy val lisa = Project(appName, file("."))
enablePlugins(PlayScala, SbtDistributablesPlugin)
scalaSettings
defaultSettings()
disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

scalaVersion := "2.13.16"

libraryDependencies ++= AppDependencies()

Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"

Test / fork := true

CodeCoverageSettings()

scalacOptions += "-Wconf:src=routes/.*:s"

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
