package com.advancedtelematic.director.http

import akka.http.scaladsl.server.{Directives, _}
import com.advancedtelematic.diff_service.client.DiffServiceClient
import com.advancedtelematic.diff_service.http.DiffResource
import com.advancedtelematic.director.VersionInfo
import com.advancedtelematic.director.manifest.Verifier.Verifier
import com.advancedtelematic.director.roles.Roles
import com.advancedtelematic.libats.http.ErrorHandler
import com.advancedtelematic.libats.http.DefaultRejectionHandler.rejectionHandler
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libtuf.data.TufDataType.TufKey
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext


class DirectorRoutes(verifier: TufKey => Verifier,
                     keyserverClient: KeyserverClient,
                     roles: Roles,
                     diffService: DiffServiceClient)
                    (implicit val db: Database,
                     ec: ExecutionContext,
                     messageBusPublisher: MessageBusPublisher) extends VersionInfo {
  import Directives._

  val extractNamespace = NamespaceDirectives.defaultNamespaceExtractor

  val routes: Route =
    handleRejections(rejectionHandler) {
      ErrorHandler.handleErrors {
        pathPrefix("api" / "v1") {
          new AdminResource(extractNamespace, keyserverClient).route ~
          new AssignmentsResource(extractNamespace).route ~
          new DeviceResource(extractNamespace, verifier, keyserverClient, roles).route ~
          new MultiTargetUpdatesResource(extractNamespace).route ~
          new DiffResource(extractNamespace, diffService).route
        }
      }
    }
}
