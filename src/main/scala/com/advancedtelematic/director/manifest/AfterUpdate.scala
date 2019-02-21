package com.advancedtelematic.director.manifest

import com.advancedtelematic.director.data.MessageDataType.UpdateStatus
import com.advancedtelematic.director.data.Messages.UpdateSpec
import com.advancedtelematic.director.db.{AdminRepositorySupport, DeviceRepositorySupport, DeviceUpdate}
import com.advancedtelematic.libats.messaging.MessageBusPublisher
import com.advancedtelematic.libats.messaging_datatype.Messages.DeviceInstallationReport
import slick.driver.MySQLDriver.api._

import scala.concurrent.{ExecutionContext, Future}


class AfterDeviceManifestUpdate()
                               (implicit db: Database, ec: ExecutionContext,
                                messageBusPublisher: MessageBusPublisher)
    extends AdminRepositorySupport
    with DeviceRepositorySupport {

  def clearUpdate(report: DeviceInstallationReport): Future[Unit] =
    for {
      _ <- publishReport(report)
      _ <- if (!report.result.success) clearFailedUpdate(report) else Future.successful(())
    } yield ()

  private def clearFailedUpdate(report: DeviceInstallationReport) =
    for {
      version <- deviceRepository.getCurrentVersion(report.device)
      lastVersion <- DeviceUpdate.clearTargetsFrom(report.namespace, report.device, version)
    } yield ()

  private def publishReport(report: DeviceInstallationReport) = {
    for {
      _ <- messageBusPublisher.publish(report)
      // Support legacy UpdateSpec message
      _ <- messageBusPublisher.publish(UpdateSpec(
          report.namespace,
          report.device,
          if(report.result.success) UpdateStatus.Finished else UpdateStatus.Failed))
    } yield ()
  }

}
