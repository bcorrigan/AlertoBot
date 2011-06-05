name := "Tweet Alarm"

version := "1.0"

organization := "bc.tweetAlarm"

libraryDependencies ++= Seq( 
    "org.twitter4j" % "twitter4j-core" % "2.2.2",
    "org.twitter4j" % "twitter4j-stream" % "2.2.2",
    "net.lag" % "configgy" % "2.0.0" intransitive()
    )


scalacOptions += "-deprecation"

scalaVersion := "2.9.0-1"

retrieveManaged := true

