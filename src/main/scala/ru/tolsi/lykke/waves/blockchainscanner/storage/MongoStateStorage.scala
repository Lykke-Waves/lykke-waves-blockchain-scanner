package ru.tolsi.lykke.waves.blockchainscanner.storage

import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.MongoDBObject
import salat.annotations.Key
import salat.dao.SalatDAO
import salat.global._

import scala.concurrent.Future

case class LastBlock(@Key("_id") network: String, block: Int)

class MongoStateStorage(collection: MongoCollection) extends StateStorage {

  private object MongoLastBlockDAO extends SalatDAO[LastBlock, String](collection)

  def readLastBlock: Future[Int] =
    Future.successful(MongoLastBlockDAO.findOneById("waves").map(_.block).getOrElse(1))

  def saveLastBlock(block: Int): Future[Unit] =
    Future.successful(MongoLastBlockDAO.update(MongoDBObject("_id" -> "waves"), MongoDBObject("block" -> block), upsert = true))
}
