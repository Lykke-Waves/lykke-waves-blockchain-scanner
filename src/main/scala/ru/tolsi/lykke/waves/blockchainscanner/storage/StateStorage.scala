package ru.tolsi.lykke.waves.blockchainscanner.storage

import scala.concurrent.Future

trait StateStorage {
  def readLastBlock: Future[Int]

  def saveLastBlock(block: Int): Future[Unit]
}
