package ru.tolsi.lykke.waves.blockchainscanner

object RangeUtil {
  def fromToSeq(from: Int, to: Int, max: Int): Seq[(Int, Int)] = {
    val ranges = ((from to to by max).toSet.toSeq :+ to).sorted.sliding(2).toSeq
    (ranges.head(0), ranges.head(1)) +: ranges.tail.flatMap {
      case Seq(from, to) if from != to => Some((from + 1, to))
      case _ => None
    }
  }
}
