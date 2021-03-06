package com.advancedtelematic.director


import java.security.Security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.{Directives, Route}
import com.advancedtelematic.diff_service.client.DiffServiceDirectorClient
import com.advancedtelematic.director.http.DirectorRoutes
import com.advancedtelematic.director.manifest.SignatureVerification
import com.advancedtelematic.director.roles.{Roles, RolesGeneration}
import com.advancedtelematic.libats.http.BootApp
import com.advancedtelematic.libats.http.LogDirectives.logResponseMetrics
import com.advancedtelematic.libats.http.VersionDirectives.versionHeaders
import com.advancedtelematic.libats.http.monitoring.{MetricsSupport, ServiceHealthCheck}
import com.advancedtelematic.libats.http.tracing.Tracing
import com.advancedtelematic.libats.http.tracing.Tracing.RequestTracing
import com.advancedtelematic.libats.messaging.MessageBus
import com.advancedtelematic.libats.slick.db.DatabaseConfig
import com.advancedtelematic.libats.slick.monitoring.{DatabaseMetrics, DbHealthResource}
import com.advancedtelematic.libtuf.data.TufDataType.KeyType
import com.advancedtelematic.libtuf_server.keyserver.KeyserverHttpClient
import com.advancedtelematic.metrics.{AkkaHttpRequestMetrics, InfluxdbMetricsReporterSupport}
import com.advancedtelematic.metrics.prometheus.PrometheusMetricsSupport
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Try

object Util {
  def namedType[T](name: String): T = {
    val ru = scala.reflect.runtime.universe
    val mirror = ru.runtimeMirror(getClass.getClassLoader)
    val objectSymbol = mirror.staticModule(name)
    val mm = mirror.reflectModule(objectSymbol)
    mm.instance.asInstanceOf[T]
  }

  def mkUri(config: Config, key: String): Uri = {
    val uri = Uri(config.getString(key))
    if (!uri.isAbsolute) {
      throw new IllegalArgumentException(s"$key is not an absolute uri")
    }
    uri
  }
}

trait Settings {
  import Util._

  private lazy val _config = ConfigFactory.load()

  val host = _config.getString("server.host")
  val port = _config.getInt("server.port")

  val tufUri = mkUri(_config, "keyserver.uri")
  val tufBinaryUri = mkUri(_config, "tuf.binary.uri")

  val defaultKeyType: Try[KeyType] = {
    Try(_config.getString("daemon.defaultKeyType")).map { defaultKeyTypeName =>
      namedType[KeyType](defaultKeyTypeName)
    }
  }
}

object Boot extends BootApp
  with Directives
  with Settings
  with VersionInfo
  with DatabaseConfig
  with MetricsSupport
  with DatabaseMetrics
  with InfluxdbMetricsReporterSupport
  with AkkaHttpRequestMetrics
  with PrometheusMetricsSupport {

  implicit val _db = db

  log.info(s"Starting $version on http://$host:$port")

  lazy val tracing = Tracing.fromConfig(config, projectName)

  def keyserverClient(implicit tracing: RequestTracing) = KeyserverHttpClient(tufUri)
  implicit val msgPublisher = MessageBus.publisher(system, config)
  val diffService = new DiffServiceDirectorClient(tufBinaryUri)

  Security.addProvider(new BouncyCastleProvider())

  val routes: Route =
    DbHealthResource(versionMap, dependencies = Seq(new ServiceHealthCheck(tufUri))).route ~
    (versionHeaders(version) & requestMetrics(metricRegistry) & logResponseMetrics(projectName) & tracing.traceRequests) { implicit requestTracing: RequestTracing =>
      prometheusMetricsRoutes ~
        new DirectorRoutes(SignatureVerification.verify, keyserverClient, new Roles(new RolesGeneration(keyserverClient, diffService)), diffService).routes
    }

  Http().bindAndHandle(routes, host, port)
}
