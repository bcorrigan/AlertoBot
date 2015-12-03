package bc.tweetalarm

import net.lag.configgy.Configgy
import net.lag.logging.Logger
import scala.util.matching.Regex

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
import org.apache.commons.mail.DefaultAuthenticator

class TweetAlarm(rules: List[RuleSet], twitter: Twitter, twitterStream: TwitterStream, mailCfg: MailConfig) extends UserStreamListener {
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
      if (status.getUser.getScreenName.toLowerCase == r.account.toLowerCase) {
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
        //twitter.updateFriendship(id,false,false)
      } else {
        followedIds ::= id
      }
    }

    ids diff followedIds foreach { id =>
      log.debug("Following " + id)
      twitter.createFriendship(id)
      //twitter.updateFriendship(id,true,true)
    }
  }

  def msgUsers(r: RuleSet, status: String) {
    r.users foreach { user =>
      if (r.isReceivingNow(user, status)) {
        var msg = "@" + r.account + " " + status
        if (!user.hasEmail && msg.length > 140) {
          msg = status
        }
        if (!user.hasEmail)
          twitter.sendDirectMessage(user.name, msg)
        else {
          var email = new SimpleEmail();
          email.setHostName(mailCfg.server);
          email.addTo(user.email.get, user.name);
          email.setCharset("UTF-8")
          email.setFrom(mailCfg.user, "Alertobot");
          email.setSubject(msg);
          email.setMsg(msg + "\n\n\n\nBroadcasting out of Kilmarnock, Scotland, this is alertobot... the #1 alerting twitter bot on planet earth.");
          email.setSmtpPort(587);
          email.setAuthenticator(new DefaultAuthenticator(mailCfg.user, mailCfg.password));
          email.setTLS(true)
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

  def onStallWarning(warning: StallWarning) {}
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

case class User(name: String, tz: java.util.TimeZone, email: Option[String]) {
  def hasEmail(): Boolean = {
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

  def hoursMatch(now: Calendar, status: String): Boolean = {
    if (activeHours.isEmpty)
      return true
    activeHours.map(_.replaceAll("\\s*", "").split("-")) foreach { hRange =>
      val start = mkCalendarAtHour(hRange(0), now.getTimeZone())
      val end = mkCalendarAtHour(hRange(1), now.getTimeZone())

      if (now.before(end) && now.after(start)) {
        if (hoursExcludes.contains(hRange)) {
          hoursExcludes.get(hRange) foreach { excludeRule =>
            if (excludeRule.findFirstMatchIn(status.toLowerCase).isDefined) {
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

case class MailConfig(server: String, user: String, password: String)


