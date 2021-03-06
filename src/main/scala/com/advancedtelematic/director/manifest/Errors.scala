package com.advancedtelematic.director.manifest

import akka.http.scaladsl.model.StatusCodes
import com.advancedtelematic.director.data.DataType.Ecu
import com.advancedtelematic.libats.data.ErrorCode
import com.advancedtelematic.libats.http.Errors.{MissingEntity, RawError}

object ErrorCodes {
  val EcuNotPrimary = ErrorCode("ecu_not_primary")
  val WrongEcuSerialInEcuManifest = ErrorCode("wrong_ecu_serial_not_in_ecu_manifest")
  val EmptySignatureList = ErrorCode("empty_signature_list")
  val SignatureMethodMismatch = ErrorCode("signature_method_mismatch")
  val SignatureNotValid = ErrorCode("signature_not_valid")
}

object Errors {
  val EcuNotFound = MissingEntity[Ecu]
  val EcuNotPrimary = RawError(ErrorCodes.EcuNotPrimary, StatusCodes.BadRequest, "The claimed primary ECU is not the primary ECU for the device")
  val EmptySignatureList = RawError(ErrorCodes.EmptySignatureList, StatusCodes.BadRequest, "The given signature list is empty")
  val SignatureMethodMismatch = RawError(ErrorCodes.SignatureMethodMismatch, StatusCodes.BadRequest, "The given signature method and the stored signature method are different")
  val SignatureNotValid = RawError(ErrorCodes.SignatureNotValid, StatusCodes.BadRequest, "The given signature is not valid")
  val WrongEcuSerialInEcuManifest = RawError(ErrorCodes.WrongEcuSerialInEcuManifest, StatusCodes.BadRequest, "The ecu serial in ecu-manifest dosen't matched the one in device-manifest")
}
