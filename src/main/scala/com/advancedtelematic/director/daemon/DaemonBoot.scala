package com.advancedtelematic.director.daemon

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.director.{Settings, VersionInfo}
import com.advancedtelematic.libtuf.keyserver.KeyserverHttpClient
import com.advancedtelematic.libats.db.{BootMigrations, DatabaseConfig}
import com.advancedtelematic.libats.http.{BootApp, HealthResource}
import com.advancedtelematic.libats.messaging.MessageListener
import com.advancedtelematic.libats.messaging.daemon.MessageBusListenerActor.Subscribe
import com.advancedtelematic.libats.messaging_datatype.Messages.{CampaignLaunched, UserCreated}
import com.advancedtelematic.libats.monitoring.{DatabaseMetrics, MetricsSupport}
import com.advancedtelematic.director.data.Messages.multiTargetUpdateCreatedMessageLike

object DaemonBoot extends BootApp
    with Settings
    with VersionInfo
    with BootMigrations
    with DatabaseConfig
    with MetricsSupport
    with DatabaseMetrics {
  import com.advancedtelematic.libats.http.LogDirectives._
  import com.advancedtelematic.libats.http.VersionDirectives._

  implicit val _db = db

  log.info("Starting director daemon")

  val tuf = new KeyserverHttpClient(tufUri)

  val fileCacheDaemon = system.actorOf(FileCacheDaemon.props(tuf), "filecache-daemon")

  val createRepoListener = system.actorOf(CreateRepoActor.props(tuf), "create-repo-listener")

  val msgParser = (uc: UserCreated) => FastFuture.successful(createRepoListener ! uc)

  val userCreatedBusListener = system.actorOf(MessageListener.props[UserCreated](config, msgParser),
                                              "user-created-msg-listener")
  userCreatedBusListener ! Subscribe

  val campaignCreatedListener =
    system.actorOf(MessageListener.props[CampaignLaunched](config, CampaignWorker.action),
                   "campaign-created-msg-listener")
  campaignCreatedListener ! Subscribe

  val multiTargetUpdateListener =
    system.actorOf(MessageListener.props[MultiTargetUpdate](config, MultiTargetUpdateWorker.action),
                   "multi-target-update-created-msg-listener")
  multiTargetUpdateListener ! Subscribe

  val routes: Route = (versionHeaders(version) & logResponseMetrics(projectName)) {
    new HealthResource(db, versionMap).route
  }

  Http().bindAndHandle(routes, host, port)
}
