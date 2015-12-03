package bc.tweetalarm

import net.lag.configgy.Config
import net.lag.configgy.ConfigMap

import scala.util.control.Breaks

import twitter4j.auth.{ Authorization, AccessToken }
import twitter4j.conf._
import twitter4j._

import java.util.regex.PatternSyntaxException

import net.lag.logging.Logger

class Configurator(cfg: Config) {
  val log = Logger.get
  val myBreaks = new Breaks
  import myBreaks.{ break, breakable }

  def rules: List[RuleSet] = {
    var ruleSets: List[RuleSet] = List()

    for (userName <- cfg.getConfigMap("users").get.keys) {
      val tz = java.util.TimeZone.getTimeZone(cfg.getString("users." + userName + ".timezone").getOrElse("Europe/London"))
      val email = cfg.getString("users." + userName + ".email")
      val user = new User(userName, tz, email)

      for (ruleName <- cfg.getList("users." + userName + ".rule_sets")) {
        breakable {

          ruleSets.filter(_.name == ruleName).foreach { ruleSets =>
            ruleSets.users ::= user
            break
          }

          val account = cfg.getString("rule_sets." + ruleName + ".account").get
          val includeRegexes = cfg.getList("rule_sets." + ruleName + ".includes").toList
          val excludeRegexes = cfg.getList("rule_sets." + ruleName + ".excludes").toList
          val activeHours = cfg.getList("rule_sets." + ruleName + ".activeHours").toList
          val activeDays = cfg.getList("rule_sets." + ruleName + ".activeDays").toList

          //there must be nicer way of doing this but damned if i know
          val hoursExcludes = (activeHours map { hRange =>
            val rangeExcludes = cfg.getList("rule_sets." + ruleName + ".hoursExclude." + hRange).map(_.r);
            if (!rangeExcludes.isEmpty) {
              (hRange, rangeExcludes)
            } else ("", Nil)
          } filterNot (_._1.equals(""))).toMap;

          try {
            ruleSets ::= new RuleSet(ruleName,
              account,
              includeRegexes.map(_.r),
              excludeRegexes.map(_.r),
              activeDays,
              activeHours,
              hoursExcludes,
              user :: Nil)
          } catch {
            case ex: PatternSyntaxException =>
              log.error("Broken rule - regular expression invalid: " + ex.getMessage)
              System.exit(1337)
          }

        }
      }
    }
    log.info("RuleSets: " + ruleSets)
    ruleSets
  }

  def twitterStream(twitter: Twitter): TwitterStream = {
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

  def mailCfg = new MailConfig(
    cfg.getString("mail.server").get,
    cfg.getString("mail.user").get,
    cfg.getString("mail.password").get)

}