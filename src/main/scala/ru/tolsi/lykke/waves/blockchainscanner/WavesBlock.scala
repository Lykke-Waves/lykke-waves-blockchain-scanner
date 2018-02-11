package ru.tolsi.lykke.waves.blockchainscanner

import play.api.libs.json._

import scala.util.control.NonFatal

object WavesBlock {

  implicit val wavesBlockReads = new Reads[WavesBlock] {
    override def reads(json: JsValue): JsResult[WavesBlock] = {
      try {
        val obj = json.as[JsObject].value
        val version = obj("version").as[Int]
        val timestamp = obj("timestamp").as[Long]
        val reference = obj("reference").as[String]
        val transactionsArray = obj("transactions").as[Seq[JsObject]]
        val transactions = WavesApi.parseOnlyTransfersAndIssuesFromJson(transactionsArray)
        val generator = obj("generator").as[String]
        val signature = obj("signature").as[String]
        val fee = obj("fee").as[Long]
        val height = obj.get("height").map(_.as[Int])
        JsSuccess(new WavesBlock(version, timestamp, reference, transactions, generator, signature, fee, height))
      } catch {
        case NonFatal(e) =>
          JsError(e.getMessage)
      }
    }
  }
}

case class WavesBlock(version: Int, timestamp: Long, reference: String, transactions: Seq[WavesTransaction], generator: String, signature: String, fee: Long, height: Option[Int])
