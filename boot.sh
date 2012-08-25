SCALA_HOME=/home/goat/scala
export SCALA_HOME
PATH=$PATH":/home/goat/bin":"/home/goat/scala/bin"
export PATH

CLASSPATH=".:"$SCALA_HOME"/lib/scala-library.jar:/home/goat/alertobot/lib_managed/jar/net.lag/configgy/configgy-2.0.0.jar:/home/goat/alertobot/lib_managed/jar/org.twitter4j/twitter4j-core/twitter4j-core-2.2.6.jar:/home/goat/alertobot/lib_managed/jar/org.twitter4j/twitter4j-stream/twitter4j-stream-2.2.6.jar:/home/goat/alertobot/target/scala-2.9.0.1/tweet-alarm_2.9.0-1-1.0.jar"
export CLASSPATH

echo $CLASSPATH

/home/goat/scala/bin/scala -classpath $CLASSPATH bc.tweetalarm.TweetAlarm