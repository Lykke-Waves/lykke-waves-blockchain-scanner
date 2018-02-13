package ru.tolsi.lykke.waves.blockchainscanner

import ru.tolsi.lykke.common.api.{WavesApi, WavesBlock}

class WavesBlockScanner(override val from: Int,
                        override val by: Int,
                        override val api: WavesApi,
                        override val confirmations: Int,
                        override val notifyBlocks: Seq[WavesBlock] => Unit) extends Scanner {
  override def toString = s"Waves block scanner [lastSeenHeight = $lastSeenHeight]"

  override def network = "waves"
}
