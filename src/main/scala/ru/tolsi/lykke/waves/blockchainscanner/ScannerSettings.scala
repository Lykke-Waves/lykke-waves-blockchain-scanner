package ru.tolsi.lykke.waves.blockchainscanner

import java.net.URL

import com.mongodb.ServerAddress
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.json._
import ru.tolsi.lykke.common.NetworkType

import scala.util.Try

object ScannerSettings extends StrictLogging {
  val Default = ScannerSettings(NetworkType.Main, ServerAddress.defaultHost(), ServerAddress.defaultPort())

  implicit val scannerSettingsReader: Reads[ScannerSettings] = Json.reads[ScannerSettings]

  def loadSettings(pathOpt: Option[String]): ScannerSettings = {
    val contentStreamOpt = pathOpt.map(u => new URL(u).openStream)
    contentStreamOpt.flatMap(c => Try {
      Json.parse(c).as[ScannerSettings]
    }.toOption).getOrElse {
      logger.warn(s"Can't read config from 'SettingsUrl', load defaults: ${ScannerSettings.Default}")
      ScannerSettings.Default
    }
  }
}

case class ScannerSettings(NetworkType: NetworkType, MongoDBHost: String, MongoDBPort: Int)