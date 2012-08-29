package bc.tweetalarm

import net.lag.configgy.Configgy
import net.lag.configgy.Config
import net.lag.configgy.ConfigMap
import net.lag.logging.Logger
import scala.util.matching.Regex
import scala.util.control.Breaks
import collection.JavaConversions._
import java.util.regex.PatternSyntaxException
import twitter4j.auth.{ Authorization, AccessToken }
import twitter4j.conf._
import twitter4j._
import java.util.TimeZone
import java.util.Calendar
import java.util.TimeZone
import java.util.GregorianCalendar
import org.apache.commons.mail.SimpleEmail

class TweetAlarm(rules: List[RuleSet], twitter: Twitter, twitterStream: TwitterStream, mailCfg:MailConfig) extends UserStreamListener {
  val log = Logger.get
  twitterStream.addListener(this)

  syncFollowed

  twitterStream.user
  log.info("TweetAlarm booted, status listener kicked off.")

  def onException(e: Exception) {
    log.error("Internal error")
    e.printStackTrace()
  }

  def onStatus(status: Status) {
    log.info("@" + status.getUser.getScreenName + ":" + status.getText)

    rules foreach { r =>
      if (status.getUser.getScreenName == r.account) {
        if (r.matches(status.getText)) {
          msgUsers(r, status.getText)
        }
      }
    }
  }

  def syncFollowed {
    val users = rules.foldLeft(List[User]()) { (r1, r2) =>
      r1 ++ r2.users
    }.distinct

    val accounts = rules.foldLeft(List[String]()) { (r1, r2) =>
      r2.account :: r1
    }.distinct

    val ids = twitter.lookupUsers(accounts.toArray: Array[String]).map(_.getId).toArray

    var followedIds: List[Long] = Nil

    twitter.getFriendsIDs(-1).getIDs foreach { id =>
      if (!ids.contains(id)) {
        log.debug("Unfollowing " + id)
        twitter.destroyFriendship(id)
        twitter.disableNotification(id)
      } else {
        followedIds ::= id
      }
    }

    ids diff followedIds foreach { id =>
      log.debug("Following " + id)
      twitter.createFriendship(id)
      twitter.enableNotification(id)
    }
  }
  
  def msgUsers(r:RuleSet, status: String) {
    r.users foreach { user =>
      if (r.isReceivingNow(user, status)) {
        var msg = "@" + r.account + " " + status
        if (!user.hasEmail && msg.length > 140) {
          msg = status
        }
        if(!user.hasEmail)
        	twitter.sendDirectMessage(user.name, msg)
        else {
        	 var email = new SimpleEmail();
        	 email.setHostName(mailCfg.server);
        	 email.addTo(user.email.get, user.name);
        	 email.setFrom(mailCfg.user, "Alertobot");
        	 email.setSubject("!" + msg);
        	 email.setMsg(msg + """     
        			 \n\n\n\nBroadcasting out of Kilmarnock, Scotland, this is alertobot... the #1 alerting twitter bot on planet earth.
        	     """);
        	 email.send();
        }
      } else {
        log.debug("Not sending to " + user.name + " cos it is outside their rule time window.")
      }
    }
  }

  def onDeletionNotice(directMessageId: Long, userId: Long) {
    log.warning("Received notice this account is deleted, rule probably knacked!")
  }

  def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {
    log.warning("Received notice this account is deleted, rule probably knacked!")
  }

  def onBlock(source: twitter4j.User, blockedUser: twitter4j.User) {}

  def onDirectMessage(directMessage: DirectMessage) {
    log.info("Got a message from " + directMessage.getSender.getScreenName + ":" + directMessage.getText)
  }

  def onFavorite(source: twitter4j.User, target: twitter4j.User, favouritedStatus: Status) {}

  def onFollow(source: twitter4j.User, followedUser: twitter4j.User) {}

  def onFriendList(friendIds: Array[Long]) {}

  def onUnblock(source: twitter4j.User, blockedUser: twitter4j.User) {}

  def onUnfavorite(source: twitter4j.User, target: twitter4j.User, favouritedStatus: Status) {}

  def onUnfollow(source: twitter4j.User, followedUser: twitter4j.User) {}

  def onUserListCreation(listOwner: twitter4j.User, list: UserList) {}

  def onUserListDeletion(listOwner: twitter4j.User, list: UserList) {}

  def onUserListMemberAddition(addedMember: twitter4j.User, listOwner: twitter4j.User, list: UserList) {}

  def onUserListMemberDeletion(addedMember: twitter4j.User, listOwner: twitter4j.User, list: UserList) {}

  def onUserListSubscription(subscriber: twitter4j.User, listOwner: twitter4j.User, list: UserList) {}

  def onUserListUnsubscription(subscriber: twitter4j.User, listOwner: twitter4j.User, list: UserList) {}

  def onUserListUpdate(listOwner: twitter4j.User, list: UserList) {}

  def onUserProfileUpdate(updatedUser: twitter4j.User) {}

  def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}

  def onScrubGeo(userId: Long, upToStatusId: Long) {}

  def onRetweet(source: twitter4j.User, target: twitter4j.User, retweetedStatus: Status) {}
}

object TweetAlarm {
  def main(args: Array[String]): Unit = {

    Configgy.configure("rules.conf")
    val config = Configgy.config

    val log = Logger.get
    val cfgurator = new Configurator(config)
    val twitter = cfgurator.twitter
    val tweetAlarm = new TweetAlarm(cfgurator.rules, 
    								twitter, 
    								cfgurator.twitterStream(twitter),
    								cfgurator.mailCfg);
  }
}

case class User(name: String, tz: java.util.TimeZone, email:Option[String]) {
  def hasEmail():Boolean = {
    return email.isDefined
  }
}

case class RuleSet(name: String,
  account: String,
  includes: Seq[Regex],
  excludes: Seq[Regex],
  activeDays: Seq[String], //if empty, all days are active
  activeHours: Seq[String], //if empty, all hours are active
  hoursExcludes: Map[String, Seq[Regex]],
  var users: List[User]) {

  val log = Logger.get

  //We don't filter on time here as that is per user
  def matches(status: String): Boolean = {
    includes foreach { includeRule =>
      if (includeRule.findFirstMatchIn(status.toLowerCase).isDefined) {
        log.debug("Rule hit! :" + includeRule + " Now test excludes.")
        excludes foreach { excludeRule =>
          if (excludeRule.findFirstMatchIn(status.toLowerCase).isDefined) {
            log.debug("We have a countervailing excludes rule hit - ignoring because of:" + excludeRule)
            return false
          }
        }
        return true
      }
    }

    return false
  }

  def isReceivingNow(user: User, status: String): Boolean = {
    //inspect activeDays and activeHours to work this out
    val now = new GregorianCalendar(user.tz)
    return hoursMatch(now, status) && daysMatch(now)
  }

  def hoursMatch(now: Calendar, status:String): Boolean = {
    if (activeHours.isEmpty)
      return true
    activeHours.map(_.replaceAll("\\s*", "").split("-")) foreach { hRange =>
      val start = mkCalendarAtHour(hRange(0), now.getTimeZone())
      val end = mkCalendarAtHour(hRange(1), now.getTimeZone())

      if (now.before(end) && now.after(start)) {
        if(hoursExcludes.contains(hRange)) {
          hoursExcludes.get(hRange) foreach { excludeRule =>
            if(excludeRule.findFirstMatchIn(status.toLowerCase).isDefined) {
              return false
            }
          }
        }
        return true 
      }
    }
    return false;
  }

  private def mkCalendarAtHour(hour: String, tz: java.util.TimeZone): Calendar = {
    val cal = new GregorianCalendar(tz)
    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hour))
    cal.set(Calendar.MINUTE, 0)
    
    cal
  }

  def daysMatch(now: Calendar): Boolean = {
    if (activeDays.isEmpty)
      return true

    activeDays foreach { sday =>
      val calDay = now.get(Calendar.DAY_OF_WEEK)
      val cfgCalDay = sday match {
        case "Mon" => Calendar.MONDAY
        case "Tue" => Calendar.TUESDAY
        case "Wed" => Calendar.WEDNESDAY
        case "Thu" => Calendar.THURSDAY
        case "Fri" => Calendar.FRIDAY
        case "Sat" => Calendar.SATURDAY
        case "Sun" => Calendar.SUNDAY
      }
      if (cfgCalDay == calDay)
        return true
    }
    return false
  }


}

case class MailConfig(server:String, user:String, password:String)

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
    					cfg.getString("mail.password").get
    				)
}
