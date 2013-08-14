name := "Tweet Alarm"

version := "1.0"

organization := "bc.tweetAlarm"

libraryDependencies ++= Seq( 
    "org.twitter4j" % "twitter4j-core" % "3.0.3",
    "org.twitter4j" % "twitter4j-stream" % "3.0.3",
    "org.apache.commons" % "commons-email" % "1.2",
    "net.lag" % "configgy" % "2.0.0" intransitive()
    )


scalacOptions += "-deprecation"

scalaVersion := "2.9.3"

retrieveManaged := true

