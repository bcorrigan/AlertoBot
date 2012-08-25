Alertobot
=========

Alertobot is a simple twitter daemon that can be configured to follow (usually spammy) users, applying configurable regex rules to each tweet they send, and if the rules pass, forwarding it via DM to specified users.

Background
----------

One day I was fed up of not discovering that my train line was disrupted by snow or wind or act of God, until I had risen from bed and reached the train station and saw the big "CANCELLED" sign in the station.

How much better would it be to know immediately, so you can work from home or travel via alternate means?

It turns out that twitter is jam packed with bots that spew out traffic and rail snafus. But, they are far too spammy to directly follow yourself.

So I created alertobot. Alertobot will apply rulesets against specified twitter accounts it should follow. These rule sets can be associated with users who will be notified.

If you are only interested in Kilmarnock-Glasgow Central trainline incidents, alertobot tells you only of those, if you make a ruleset that filters @NREScotrail's tweets.

When alertobot finds a matching tweet, it will direct message you. If your phone is set up you should therefore receive a highly visible alert the moment there is a problem.

Alertobot is a simple daemon written in scala using the SBT build system. Edit rules.conf appropriately, and background alertobot on some server, forever. 

Features
--------

Applies list of regex rules to tweets recieved. If the tweets pass, forwards them to designated accounts.

Can be supplied with negative rules - if a negative rule matches, the tweet is NOT forwarded.

Each ruleset can be given "active hours" and "active days". Active hours are the hours of the day the ruleset is active for - otherwise it is suppressed. And active days is similar, but for days of the week. If not specified then 24/7 is assumed.

Configuration
-------------

Check out rules.conf above - it contains most everything you need to know and is fairly self explanatory.

