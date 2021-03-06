package com.advancedtelematic.director.repo

import com.advancedtelematic.director.db.RepoNameRepositorySupport
import com.advancedtelematic.director.db.Errors.MissingNamespaceRepo
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libtuf.data.TufDataType.{KeyType, RepoId}
import com.advancedtelematic.libtuf_server.keyserver.KeyserverClient
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api.Database

class DirectorRepo(keyserverClient: KeyserverClient)(implicit db: Database, ec: ExecutionContext) extends RepoNameRepositorySupport {

  def findOrCreate(namespace: Namespace, keyType: KeyType): Future[RepoId] = {
    repoNameRepository.getRepo(namespace).recoverWith {
      case MissingNamespaceRepo =>
        val repoId = RepoId.generate

        keyserverClient.createRoot(repoId, keyType).flatMap { _ =>
          repoNameRepository.persist(namespace, repoId)
        }.map(_ => repoId)
    }
  }

}
