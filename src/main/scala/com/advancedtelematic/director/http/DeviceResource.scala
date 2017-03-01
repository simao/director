package com.advancedtelematic.director.http

import akka.http.scaladsl.server.Directive1
import akka.stream.Materializer
import com.advancedtelematic.director.client.CoreClient
import com.advancedtelematic.director.data.Codecs._
import com.advancedtelematic.director.data.DataType.{DeviceId, Namespace}
import com.advancedtelematic.director.data.DeviceRequest.DeviceManifest
import com.advancedtelematic.director.db.{DeviceRepositorySupport, DeviceUpdate,
  FileCacheRepositorySupport, RootFilesRepositorySupport}
import com.advancedtelematic.director.manifest.Verifier.Verifier
import com.advancedtelematic.director.manifest.Verify
import com.advancedtelematic.libtuf.data.ClientDataType.ClientKey
import com.advancedtelematic.libtuf.data.TufCodecs._
import com.advancedtelematic.libtuf.data.TufDataType.SignedPayload
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import org.slf4j.LoggerFactory
import scala.async.Async._
import scala.concurrent.{ExecutionContext, Future}
import slick.driver.MySQLDriver.api._

class DeviceResource(extractNamespace: Directive1[Namespace],
                     verifier: ClientKey => Verifier,
                     coreClient: CoreClient)
                    (implicit db: Database, ec: ExecutionContext, mat: Materializer)
    extends DeviceRepositorySupport
    with FileCacheRepositorySupport
    with RootFilesRepositorySupport {
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.Route

  private lazy val _log = LoggerFactory.getLogger(this.getClass)

  def setDeviceManifest(namespace: Namespace, signedDevMan: SignedPayload[DeviceManifest]): Route = {
    val device = signedDevMan.signed.vin
    val action = async {
      val ecus = await(deviceRepository.findEcus(namespace, device))
      val ecuImages = await(Future.fromTry(Verify.deviceManifest(ecus, verifier, signedDevMan)))

      await(DeviceUpdate.setEcus(coreClient)(namespace, device, ecuImages))
    }
    complete(action)
  }

  def fetchTargets(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchTarget(device, version)
    }
    complete(action)
  }

  def fetchSnapshot(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchSnapshot(device, version)
    }
    complete(action)
  }

  def fetchTimestamp(ns: Namespace, device: DeviceId): Route = {
    val action = deviceRepository.getNextVersion(device).flatMap { version =>
      fileCacheRepository.fetchTimestamp(device, version)
    }
    complete(action)
  }

  def fetchRoot(ns: Namespace): Route = {
    complete(rootFilesRepository.find(ns))
  }

  val route = extractNamespace { ns =>
    pathPrefix("device") {
      path("manifest") {
        (put & entity(as[SignedPayload[DeviceManifest]])) { devMan =>
          setDeviceManifest(ns, devMan)
        }
      } ~
      pathPrefix(DeviceId.Path) { device =>
        get {
          path("root.json") {
            fetchRoot(ns)
          } ~
          path("targets.json") {
            fetchTargets(ns, device)
          } ~
          path("snapshots.json") {
            fetchSnapshot(ns, device)
          } ~
          path("timestamp.json") {
            fetchTimestamp(ns, device)
          }
        }
      }
    }
  }
}
