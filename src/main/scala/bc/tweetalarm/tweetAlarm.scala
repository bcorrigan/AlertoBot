package bc.tweetalarm

import net.lag.configgy.Configgy
import net.lag.configgy.Config
import net.lag.configgy.ConfigMap
import net.lag.logging.Logger
import scala.util.matching.Regex
import scala.util.control.Breaks
import java.util.regex.PatternSyntaxException

import twitter4j.auth.{ Authorization, AccessToken }
import twitter4j.conf._
import twitter4j._

class TweetAlarm(rules: List[RuleSet], twitter:Twitter, twitterStream: TwitterStream) extends StatusListener {
  val log = Logger.get
  twitterStream.addListener(this)
  log.info("TweetAlarm booted, status listener kicked off.")

  val users = rules.foldLeft(List[User]()) { (r1,r2) =>
      			r1++r2.users
    		  }.distinct
  
  log.info("Uniq users:" + users)
  
  def onException(e: Exception) {
    //pass
    e.printStackTrace()
  }

  def onStatus(status: Status) {
    log.info("msg received, ID:" + status.getUser.getId)
    log.info("text:" + status.getText)
  }

  def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {
    log.warning("Received notice this account is deleted, rule probably knacked!")
  }

  def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {
    //ignore, now and forever
  }

  def onScrubGeo(userId: Long, upToStatusId: Long) {
    //who gives a shit
  }
}

object TweetAlarm {
  def main(args: Array[String]): Unit = {
    Configgy.configure("src/main/resources/rules.conf")
    val config = Configgy.config
    val log = Logger.get
    log.info("STUB WORKS!")
    val cfgurator = new Configurator(config)
    val ruleSet = cfgurator.rules
    val twitter = cfgurator.twitter
    val twitterStream = cfgurator.twitterStream(twitter)
    val tweetAlarm = new TweetAlarm(ruleSet, twitter, twitterStream);

  }
}

case class User(name: String, email: String)

case class RuleSet(name: String, account: String, rules: List[Regex], var users: List[User]) {

}

class Configurator(cfg: Config) {
  val log = Logger.get
  val myBreaks = new Breaks
  import myBreaks.{ break, breakable }

  def rules: List[RuleSet] = {
    var ruleSets: List[RuleSet] = List()

    for (userName <- cfg.getConfigMap("users").get.keys) {
      val email = cfg.getString("users." + userName + ".email").get
      val user = new User(userName, email)
      log.info("Found user: " + user)
      breakable {
        for (ruleName <- cfg.getList("users." + userName + ".rule_sets")) {
          log.info("rule_name:" + ruleName)

          ruleSets.filter(_.name == ruleName).foreach { ruleSets =>
            ruleSets.users ::= user
            log.debug("Found matching ruleset, just adding user and breaking.")
            break
          }

          log.info("Unknown rule_set defined for user")
          val account = cfg.getString("rule_sets." + ruleName + ".account").get
          val ruleRegexes = cfg.getList("rule_sets." + ruleName + ".rules").toList

          try {
            ruleSets ::= new RuleSet(ruleName, account, ruleRegexes.map(_.r), user :: Nil)
          } catch {
            case ex: PatternSyntaxException =>
              log.error("Broken rule - regular expression invalid: " + ex.getMessage)
              System.exit(1337)
          }
          log.info("Appended ruleSet:" + ruleSets)
        }
      }
    }

    ruleSets
  }

  def twitterStream(twitter:Twitter): TwitterStream = {
    new TwitterStreamFactory().getInstance(twitter.getAuthorization())
  }
  
  def twitter: Twitter = {
    val cb = new ConfigurationBuilder();

    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(cfg.getString("twitter.consumerKey").get)
      .setOAuthConsumerSecret(cfg.getString("twitter.consumerSecret").get)
      .setOAuthAccessToken(cfg.getString("twitter.accessToken").get)
      .setOAuthAccessTokenSecret(cfg.getString("twitter.accessTokenSecret").get);

    new TwitterFactory(cb.build()).getInstance()
  }
}