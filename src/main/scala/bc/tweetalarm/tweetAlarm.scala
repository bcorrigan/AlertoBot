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

class TweetAlarm(rules: List[RuleSet], twitter:Twitter, twitterStream: TwitterStream) extends UserStreamListener {
  val log = Logger.get
  twitterStream.addListener(this)

  val users = rules.foldLeft(List[User]()) { (r1,r2) =>
      			r1++r2.users
    		  }.distinct

  val accounts = rules.foldLeft(List[String]()) { (r1,r2) =>
      			r2.account :: r1
    		  }.distinct
    		  
  log.debug("Uniq users:" + users)
  log.debug("accounts:" + accounts)
  val ids = twitter.lookupUsers(accounts.toArray:Array[String]).map(_.getId).toArray
  
  ids foreach { id => log.debug("id:" + id) }
  
  var followedIds:List[Long] = Nil
  
  twitter.getFriendsIDs(-1).getIDs foreach {id =>
    if(!ids.contains(id)) {
        log.debug("disabling " + id)
    	twitter.destroyFriendship(id)
    	twitter.disableNotification(id)
    } else {
      followedIds ::= id
    }
  }
    		  
  ids diff followedIds foreach {id =>
    log.debug("Notifying for " + id)
    twitter.createFriendship(id)
    twitter.enableNotification(id)
  }
  
  twitterStream.user
  log.info("TweetAlarm booted, status listener kicked off.")
  
  def onException(e: Exception) {
    log.error("Internal error")
    e.printStackTrace()
  }

  def onStatus(status: Status) {
    log.info("msg received, ID:" + status.getUser.getId)
    log.info("text:" + status.getText)
    
    rules foreach { r =>
      if(status.getUser.getScreenName == r.account) {
        if(r.matches(status.getText)) {
          r.tweetUsers(twitter, status.getText)
        }
      }
    }
  }

  def onDeletionNotice(directMessageId:Long, userId:Long) {
    log.warning("Received notice this account is deleted, rule probably knacked!")
  }
  
  def onDeletionNotice(statusDeletionNotice:StatusDeletionNotice) {
    log.warning("Received notice this account is deleted, rule probably knacked!")
  }

  def onBlock(source:twitter4j.User, blockedUser:twitter4j.User) { }
  
  def onDirectMessage(directMessage:DirectMessage) {
    log.info("Got a message from " + directMessage.getSender.getScreenName + ":" + directMessage.getText)
  }
  
  def onFavorite(source:twitter4j.User, target:twitter4j.User, favouritedStatus:Status) { }
  
  def onFollow(source:twitter4j.User, followedUser:twitter4j.User) { }
  
  def onFriendList(friendIds:Array[Long]) { }
  
  def onUnblock(source:twitter4j.User, blockedUser:twitter4j.User) { }
  
  def onUnfavorite(source:twitter4j.User, target:twitter4j.User, favouritedStatus:Status) { }
  
  def onUnfollow(source:twitter4j.User, followedUser:twitter4j.User) { }
  
  def onUserListCreation(listOwner:twitter4j.User, list:UserList) { }
  
  def onUserListDeletion(listOwner:twitter4j.User, list:UserList) { }
  
  def onUserListMemberAddition(addedMember:twitter4j.User, listOwner:twitter4j.User, list:UserList) { }
  
  def onUserListMemberDeletion(addedMember:twitter4j.User, listOwner:twitter4j.User, list:UserList) { }
  
  def onUserListSubscription(subscriber:twitter4j.User, listOwner:twitter4j.User, list:UserList ) { }
  
  def onUserListUnsubscription(subscriber:twitter4j.User, listOwner:twitter4j.User, list:UserList ) { }
  
  def onUserListUpdate(listOwner:twitter4j.User, list:UserList) { }
  
  def onUserProfileUpdate(updatedUser:twitter4j.User)  { }
  
  def onTrackLimitationNotice(numberOfLimitedStatuses:Int) { }
  
  def onScrubGeo(userId:Long, upToStatusId:Long) { }
  
  def onRetweet(source:twitter4j.User, target:twitter4j.User, retweetedStatus:Status) { } 
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

case class User(name: String)

case class RuleSet(name: String, account: String, rules: List[Regex], var users: List[User]) {
  
  val log = Logger.get
  
  //only one rule needs to match
  def matches(status:String):Boolean = {
    rules foreach { regex =>
      log.debug("Applying regex:" + regex)
      log.debug("Matches:" + regex.findFirstMatchIn(status.toLowerCase).isDefined)
      if( regex.findFirstMatchIn(status.toLowerCase).isDefined )
        return true
    }
    
    return false
  }
  
  def tweetUsers(twitter:Twitter, status:String) {
    users foreach { user =>
      twitter.updateStatus("@" + user.name + " @" + account + " " + status)
    }
  }
}

class Configurator(cfg: Config) {
  val log = Logger.get
  val myBreaks = new Breaks
  import myBreaks.{ break, breakable }

  def rules: List[RuleSet] = {
    var ruleSets: List[RuleSet] = List()

    for (userName <- cfg.getConfigMap("users").get.keys) {
      val user = new User(userName)
      log.info("Found user: " + user)

      for (ruleName <- cfg.getList("users." + userName + ".rule_sets")) {
        breakable {
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