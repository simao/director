package com.advancedtelematic.director.http

import java.security.{KeyPairGenerator, PublicKey}
import java.util.concurrent.ConcurrentHashMap

import com.advancedtelematic.director.data.Codecs._
import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.AdminRequest._
import com.advancedtelematic.director.data.Codecs.encoderEcuManifest
import com.advancedtelematic.director.data.DataType._
import com.advancedtelematic.director.data.GeneratorOps._
import com.advancedtelematic.director.data.{EdGenerators, KeyGenerators, RsaGenerators}
import com.advancedtelematic.director.db.{DeviceRepositorySupport, FileCacheDB, SetTargets}
import com.advancedtelematic.director.manifest.Verifier
import com.advancedtelematic.director.util.NamespaceTag.NamespaceTag
import com.advancedtelematic.director.util.{DefaultPatience, DirectorSpec, RouteResourceSpec}
import com.advancedtelematic.libats.data.DataType.CampaignId
import com.advancedtelematic.libats.messaging_datatype.DataType.DeviceId
import com.advancedtelematic.libtuf.data.TufDataType.{RSATufKey, TufKey}
import org.scalatest.Inspectors

trait DeviceResourceSpec extends DirectorSpec with KeyGenerators with DefaultPatience with DeviceRepositorySupport
    with FileCacheDB with RouteResourceSpec with NamespacedRequests with Inspectors {

  val correlationId = CampaignId(java.util.UUID.randomUUID())

  def schedule(device: DeviceId, targets: SetTarget)(implicit ns: NamespaceTag): Unit = {
    SetTargets.setTargets(ns.get, Seq(device -> targets), Some(correlationId)).futureValue
    pretendToGenerate().futureValue
  }

  def deviceVersion(deviceId: DeviceId): Option[Int] = {
    deviceRepository.getCurrentVersion(deviceId).map(Some.apply).recover{case _ => None}.futureValue
  }

  def deviceScheduledVersion(deviceId: DeviceId)(implicit ns: NamespaceTag): Int = {
    fetchTargetsFor(deviceId).signed.version
  }

  testWithNamespace("Can register device") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = GenRegisterEcu.atMost(5).generate ++ (primEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)
  }

  testWithNamespace("Can't register device with primary ECU not in `ecus`") { implicit ns =>
    val device = DeviceId.generate()
    val primEcu = GenEcuIdentifier.generate
    val ecus = GenRegisterEcu.atMost(5).generate.filter(_.ecu_serial != primEcu)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceExpected(regDev, StatusCodes.BadRequest)
  }

  testWithNamespace("Device can update a registered device") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = GenRegisterEcu.atMost(5).generate ++ (primEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }

    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)
  }

  testWithNamespace("Device update with broken image hash fails") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = GenRegisterEcu.atMost(5).generate ++ (primEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu =>
      GenSigned(GenImageInvalidHash.flatMap(GenEcuManifestWithImage(regEcu.ecu_serial, _, None))).generate
    }

    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestExpect(device, deviceManifest, StatusCodes.BadRequest)
  }

  testWithNamespace("Device can update a registered device (legacy device manifest)") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = GenRegisterEcu.atMost(5).generate ++ (primEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }

    val deviceManifest = GenSignedLegacyDeviceManifest(primEcu, ecuManifests).generate

    updateLegacyManifestOk(device, deviceManifest)
  }

  testWithNamespace("Device must have the ecu given as primary") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val fakePrimEcu = GenEcuIdentifier.generate
    val ecus = GenRegisterEcu.atMost(5).generate ++
      (primEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }

    val deviceManifest = GenSignedDeviceManifest(fakePrimEcu, ecuManifests).generate

    updateManifestExpect(device, deviceManifest, StatusCodes.NotFound)
  }

  testWithNamespace("Device need to have the correct primary") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val fakePrimEcuReg = GenRegisterEcu.generate
    val fakePrimEcu = fakePrimEcuReg.ecu_serial
    val ecus = GenRegisterEcu.atMost(5).generate ++
      (primEcuReg :: fakePrimEcuReg :: GenRegisterEcu.atMost(5).generate)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }

    val deviceManifest = GenSignedDeviceManifest(fakePrimEcu, ecuManifests).generate

    updateManifestExpect(device, deviceManifest, StatusCodes.BadRequest)
  }

  testWithNamespace("Device update will only update correct ecus") { implicit ns =>
    val taintedKeys = new ConcurrentHashMap[PublicKey, Unit]() // this is like a set
    def testVerifier(c: TufKey): Verifier.Verifier =
      if (taintedKeys.contains(c.keyval)) {
        Verifier.alwaysReject
      } else {
        Verifier.alwaysAccept
      }

    val verifyRoutes = routesWithVerifier(testVerifier)


    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecusWork = GenRegisterEcu.atMost(5).generate ++ (primEcuReg :: GenRegisterEcu.atMost(5).generate)
    val ecusFail = GenEcuIdentifier.nonEmptyAtMost(5).generate.map{ ecu =>
      val regEcu = GenRegisterEcu.generate
      taintedKeys.put(regEcu.clientKey.keyval, Unit)
      regEcu
    }
    val ecus = ecusWork ++ ecusFail

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOkWith(regDev, verifyRoutes)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }

    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOkWith(device, deviceManifest, verifyRoutes)

    val images = getInstalledImagesOkWith(device, verifyRoutes)

    val mImages = {
      val start = images.groupBy(_._1).mapValues(_.map(_._2))
      start.values.foreach { x =>
        x.length shouldBe 1
      }

      start.mapValues(_.head)
    }

    ecus.zip(ecuManifests.map(_.signed)).foreach { case (regEcu, ecuMan) =>
      if (regEcu.clientKey.keyval.getFormat() == "REJECT ME") {
        mImages.get(regEcu.ecu_serial) shouldBe None
        } else {
        mImages.get(regEcu.ecu_serial) shouldBe Some(ecuMan.installed_image)
      }
    }
  }

  testWithNamespace("Can set target for device") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val targets = SetTarget(Map(primEcu -> GenCustomImage.generate))

    setTargetsOk(device, targets)
  }

  testWithNamespace("Device can update to set target") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val targetImage = GenCustomImage.generate
    val targets = SetTarget(Map(primEcu -> targetImage))

    setTargetsOk(device, targets)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    val ecuManifestsTarget = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }.map { sig =>
      sig.updated(signed = sig.signed.copy(installed_image = targetImage.image))
    }
    val deviceManifestTarget = GenSignedDeviceManifest(primEcu, ecuManifestsTarget).generate

    updateManifestOk(device, deviceManifestTarget)
  }

  testWithNamespace("Device receives targets with uri if target contains uri") { implicit ns =>
    createRepoOk(testKeyType)

    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val targetImage = GenCustomImage.retryUntil(_.uri.isDefined).generate.copy(diffFormat = None)
    val targets = SetTarget(Map(primEcu -> targetImage))

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    schedule(device, targets)

    val deviceTargets = fetchTargetsFor(device).signed
    val uri = deviceTargets.targets.head._2.custom.flatMap(_.as[TargetCustom].toOption).flatMap(_.uri)

    uri shouldBe targetImage.uri
  }

  testWithNamespace("Device can report current current") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    val targetImage = GenCustomImage.generate
    val targets = SetTarget(Map(primEcu -> targetImage))

    setTargetsOk(device, targets)

    updateManifestOk(device, deviceManifest)
  }

  testWithNamespace("Update where the device is already") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    val targets = SetTarget(Map(primEcu -> CustomImage(ecuManifests.head.signed.installed_image, None, None)))

    schedule(device, targets)
    updateManifestOk(device, deviceManifest)

    deviceVersion(device) shouldBe Some(1)
  }

  testWithNamespace("First Device can also update") { implicit ns =>
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val cimage = GenCustomImage.generate

    val targets = SetTarget(Map(primEcu -> cimage))

    schedule(device, targets)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifestWithImage(regEcu.ecu_serial, cimage.image).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    deviceVersion(device) shouldBe Some(1)
  }

  testWithNamespace("Failed update doesn't increase current target version") { implicit ns =>
    createRepoOk(testKeyType)
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    val ecuManifests = ecus.map { regEcu => GenSignedEcuManifest(regEcu.ecu_serial).generate }
    val deviceManifest = GenSignedDeviceManifest(primEcu, ecuManifests).generate

    updateManifestOk(device, deviceManifest)

    val targetImage = GenCustomImage.generate
    val targets = SetTarget(Map(primEcu -> targetImage))

    schedule(device, targets)
    updateManifestOk(device, deviceManifest)

    val deviceManifest2 = GenSignedDeviceManifest(primEcu, Seq()).generate

    updateManifestOk(device, deviceManifest2)
    deviceVersion(device) shouldBe Some(2)
    deviceScheduledVersion(device) shouldBe 2

    // currently device-current-target and device-update-target are both at 2
    // sending empty device manifest should not update device-current-target to 3
    updateManifestOk(device, deviceManifest2)
    deviceVersion(device) shouldBe Some(2)
    deviceScheduledVersion(device) shouldBe 2
  }

  testWithNamespace("Device can get versioned root.json") { implicit ns =>
    createRepo(testKeyType)
    val device = DeviceId.generate()
    val primEcuReg = GenRegisterEcu.generate
    val primEcu = primEcuReg.ecu_serial
    val ecus = List(primEcuReg)

    val regDev = RegisterDevice(device, primEcu, ecus)

    registerDeviceOk(regDev)

    fetchRootFor(device).signed shouldBe fetchRootFor(device, 1).signed
  }
}

class RsaDeviceResourceSpec extends DeviceResourceSpec with RsaGenerators {
  testWithNamespace("Device can't register with a public RSA key which is too small") { implicit ns =>
    val device = DeviceId.generate
    val ecuIds = GenEcuIdentifier.listBetween(5,5).generate
    val primEcu = ecuIds.head

    val regEcusPrev = ecuIds.zipWithIndex.map { case (ecu, i) =>
      val reg = GenRegisterEcu.generate.copy(ecu_serial = ecu)
      if (i == 3) {
        // we can't use TufCrypto.generateKeyPair to generate the key since it will
        // throw an exception if the key is too small
        val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyGen.initialize(1024)
        val keyPair = keyGen.generateKeyPair()
        reg.copy(clientKey = RSATufKey(keyPair.getPublic))
      } else reg
    }

    val regEcus = regEcusPrev

    val regDev = RegisterDevice(device, primEcu, regEcus)

    registerDeviceExpected(regDev, StatusCodes.BadRequest)
  }
}

class EdDeviceResourceSpec extends DeviceResourceSpec with EdGenerators
