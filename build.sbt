organization := Common.organization

name := "s2graph"

version := Common.version

scalaVersion := Common.scalaVersion

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")

javaOptions ++= collection.JavaConversions.propertiesAsScalaMap(System.getProperties).map{ case (key, value) => "-D" + key + "=" + value }.toSeq

// I - show reminder of failed and canceled tests without stack traces
// T - show reminder of failed and canceled tests with short stack traces
// G - show reminder of failed and canceled tests with full stack traces
testOptions in Test += Tests.Argument("-oDF")

resolvers ++= Common.resolvers

lazy val root = project.in(file(".")).enablePlugins(PlayScala).dependsOn(s2core, s2counter_core)

lazy val s2core = project

lazy val spark = project

lazy val loader = project.dependsOn(s2core, spark)

lazy val s2counter_core = project.dependsOn(s2core)

lazy val s2counter_loader = project.dependsOn(s2counter_core, spark)

libraryDependencies ++= Seq(
  ws,
  filters,
  "org.json4s" %% "json4s-native" % "3.2.11"
)
