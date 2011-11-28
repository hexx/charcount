seq(conscriptSettings :_*)

organization := "com.github.hexx"

name := "CharCount"

version := "0.0.1"

scalaVersion := "2.9.1"

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "jfree" % "jfreechart" % "1.0.13",
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "org.scala-tools.time" %% "time" % "0.5",
  "com.mongodb.casbah" %% "casbah" % "2.1.5-1",
  "com.twitter" % "util-eval" % "1.12.4",
  "com.github.scopt" %% "scopt" % "1.1.2",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.2.0",
  "com.github.hexx" %% "hexx-common" % "0.0.1"
)

resolvers ++= Seq(
  "twiiter" at "http://maven.twttr.com/"
)
