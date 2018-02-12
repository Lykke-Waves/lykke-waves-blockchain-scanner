package ru.tolsi.lykke.waves.blockchainscanner

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import scorex.crypto.encode.Base58

object WavesTransaction {
  val IssueType = 3
  val TransferType = 4
}

object WavesTransferTransaction {
  implicit val wavesTransferTransactionReads = (
    (JsPath \ "id").read[String] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "sender").read[String] and
      (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "recipient").read[String] and
      (JsPath \ "amount").read[Long] and
      (JsPath \ "assetId").readNullable[String] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "feeAssetId").readNullable[String] and
      (JsPath \ "attachment").readNullable[String].map(b => if (b.nonEmpty && b.exists(_.nonEmpty)) Base58.decode(b.get).get else Array.emptyByteArray) and
      (JsPath \ "signature").read[String]
    ) { (id: String, t: Int, sender: String, senderPublicKey: String, recipient: String, amount: Long, assetId: Option[String],
         timestamp: Long, fee: Long, feeAssetId: Option[String], attachment: Array[Byte], signature: String) => {
    require(t == WavesTransaction.TransferType)
    new WavesTransferTransaction(id, sender, senderPublicKey, recipient, amount, assetId, timestamp, fee, feeAssetId, attachment, signature)
  }
  }
}

sealed trait WavesTransaction

case class WavesTransferTransaction(id: String,
                                    from: String,
                                    fromPublicKey: String,
                                    to: String,
                                    amount: Long,
                                    assetId: Option[String],
                                    timestamp: Long,
                                    fee: Long,
                                    feeAssetId: Option[String],
                                    attachment: Array[Byte],
                                    signature: String) extends WavesTransaction

object WavesIssueTransaction {
  implicit val wavesIssueTransactionReads = (
    (JsPath \ "id").read[String] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "sender").read[String] and
      (JsPath \ "senderPublicKey").read[String] and
      (JsPath \ "quantity").read[Long] and
      (JsPath \ "decimals").read[Byte] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "signature").read[String]
    ) { (id: String, t: Int, sender: String, senderPublicKey: String, amount: Long, decimals: Byte, timestamp: Long, fee: Long, name: String, description: String, signature: String) => {
    require(t == WavesTransaction.IssueType)
    new WavesIssueTransaction(id: String, sender, senderPublicKey, amount, decimals, timestamp, fee, name, description, signature)
  }
  }
}

case class WavesIssueTransaction(id: String,
                                 from: String,
                                 fromPublicKey: String,
                                 amount: Long,
                                 decimals: Byte,
                                 timestamp: Long,
                                 fee: Long,
                                 name: String,
                                 description: String,
                                 signature: String) extends WavesTransaction