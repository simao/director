package com.advancedtelematic.director.http

import akka.http.scaladsl.server.{Directives, _}
import akka.stream.Materializer
import com.advancedtelematic.director.data.DataType.Crypto
import com.advancedtelematic.director.VersionInfo
import com.advancedtelematic.director.manifest.Verify
import org.genivi.sota.http.{ErrorHandler, NamespaceDirectives, HealthResource}
import org.genivi.sota.rest.SotaRejectionHandler._

import scala.concurrent.ExecutionContext
import slick.driver.MySQLDriver.api._


class DirectorRoutes(verifier: Crypto => Verify.Verifier)
  (implicit val db: Database, ec: ExecutionContext, mat: Materializer) extends VersionInfo {
  import Directives._

  val extractNamespace = NamespaceDirectives.defaultNamespaceExtractor.map(_.namespace)


  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
            new DeviceResource(extractNamespace, verifier).route
        } ~ new HealthResource(db, versionMap).route
      }
    }
}