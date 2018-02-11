package ru.tolsi.lykke.waves.blockchainscanner

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

trait Scanner extends StrictLogging {
  def notifyBlocks: Seq[WavesBlock] => Unit

  def confirmations: Int

  def from: Int

  def by: Int

  def api: ScannerApi

  // todo remove it
  def network: String

  protected def withConfirmations(h: Int): Int = h - confirmations

  protected var lastSeenHeight = from

  def step(): Try[Int] =
    Try {
      val newHeight = Await.result(api.height, 1.minute)
      val newConfirmedHeight = withConfirmations(newHeight)
      if (newConfirmedHeight > lastSeenHeight) {
        val _from = Math.min(lastSeenHeight + 1, newConfirmedHeight)
        val _to = Math.max(lastSeenHeight + 1, newConfirmedHeight)
        logger.info(s"Start process blocks from ${_from} to ${_to} by $by")
        for {
          (from, to) <- RangeUtil.fromToSeq(_from, _to, by)
        } {
          val newBlocks = Await.result(
            api.blocksByHeightRange(from, to), 1.minute)
          logger.debug(s"Loaded ${newBlocks.size} $network blocks ${_from} - ${_to} with ids: ${newBlocks.map(_.signature).mkString(",")}")

          require(newBlocks.nonEmpty, "Blocks should be not empty")

          notifyBlocks(newBlocks)
          lastSeenHeight = to
        }
        lastSeenHeight
      } else lastSeenHeight
    }
}
