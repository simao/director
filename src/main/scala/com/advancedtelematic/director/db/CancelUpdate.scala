package com.advancedtelematic.director.db

import akka.http.scaladsl.util.FastFuture
import com.advancedtelematic.director.data.DataType.DeviceUpdateTarget
import com.advancedtelematic.libats.data.DataType.Namespace
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libats.slick.db.SlickUUIDKey._
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.MySQLProfile.api._

class CancelUpdate(implicit db: Database, ec: ExecutionContext) extends AdminRepositorySupport
    with DeviceRepositorySupport {
  private def act(namespace: Namespace, device: DeviceId): DBIO[Option[DeviceUpdateTarget]] = {
    for {
      current <- deviceRepository.getCurrentVersionAction(device).map(_.getOrElse(0))
      queuedItems = Schema.deviceTargets.filter(_.device === device).filter(_.version > current)
      // we can cancel if none of them have been served
      cantCancel <- queuedItems.map(_.served).filter(identity).exists.result
      res <- if (cantCancel) {
        DBIO.successful(None)
      } else for {
        latestVersion <- adminRepository.getLatestScheduledVersion(namespace, device)
        updateTarget <- adminRepository.fetchDeviceUpdateTargetAction(namespace, device, latestVersion)
        nextTimestampVersion = latestVersion + 1
        _ <- adminRepository.updateDeviceTargetsAction(device, None, None, nextTimestampVersion)
        _ <- deviceRepository.updateDeviceVersionAction(device, nextTimestampVersion)
      } yield Some(updateTarget)
    } yield res
  }.transactionally


  def one(ns: Namespace, device: DeviceId): Future[DeviceUpdateTarget] =
    db.run(act(ns, device)).flatMap {
      case Some(updateTarget) => FastFuture.successful(updateTarget)
      case None => FastFuture.failed(Errors.CouldNotCancelUpdate)
    }

  // returns the subset of devices that were canceled
  def several(ns: Namespace, devices: Seq[DeviceId]): Future[Seq[DeviceUpdateTarget]] = db.run {
    DBIO.sequence(devices.map(act(ns,_))).map(_.flatten)
  }
}
