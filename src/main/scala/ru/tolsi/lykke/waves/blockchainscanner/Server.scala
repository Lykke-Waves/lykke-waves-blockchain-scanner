package ru.tolsi.lykke.waves.blockchainscanner

import com.mongodb.casbah.{MongoClient, MongoCollection}
import com.typesafe.scalalogging.StrictLogging
import ru.tolsi.lykke.common.NetworkType
import ru.tolsi.lykke.common.api.{WavesApi, WavesIssueTransaction, WavesTransferTransaction}
import ru.tolsi.lykke.common.repository.mongo._
import ru.tolsi.lykke.common.repository.{Asset, AssetsStore, Transaction}
import ru.tolsi.lykke.waves.blockchainscanner.storage.MongoStateStorage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}


object Server extends App with StrictLogging {
  private val settingsUrl = sys.env.get("SettingsUrl")
  private val settings = ScannerSettings.loadSettings(settingsUrl)

  private val mongoClient = MongoClient(settings.MongoDBHost, settings.MongoDBPort)

  private val dbName = if (settings.NetworkType == NetworkType.Main) "lykke-waves" else "lykke-waves-testnet"
  private val db = mongoClient.getDB(dbName)

  private val networkName = if (settings.NetworkType == NetworkType.Main) "waves" else "waves-testnet"
  private val stateStorage = new MongoStateStorage(networkName, new MongoCollection(db.getCollection("processed")))

  private val from = Await.result(stateStorage.readLastBlock, 1 minute)

  private val apiUrl = if (settings.NetworkType == NetworkType.Main) "https://nodes.wavesnodes.com" else "https://testnodes.wavesnodes.com/"
  private val api = new WavesApi(apiUrl)

  private val operationsStore = new MongoBroadcastOperationsStore(new MongoCollection(db.getCollection("transactions")))
  private val assetsStore = new MongoAssetsStore(new MongoCollection(db.getCollection("assets")))
  private val balancesStore = new MongoBalancesStore(new MongoCollection(db.getCollection("balances")),
    new MongoCollection(db.getCollection("balances_observations")))
  private val fromAddressTransactionsStore = new MongoFromAddressTransactionsStore(new MongoCollection(db.getCollection("from_address_transactions")),
    new MongoCollection(db.getCollection("from_address_transactions_observations")))
  private val toAddressTransactionsStore = new MongoToAddressTransactionsStore(new MongoCollection(db.getCollection("to_address_transactions")),
    new MongoCollection(db.getCollection("to_address_transactions_observations")))

  private val scanner = new WavesBlockScanner(from, by = 99, api, confirmations = 20, blocks => {
    val transactions = blocks.flatMap(_.transactions)

    val transfers = transactions.collect { case t: WavesTransferTransaction => t }
    val issues = transactions.collect { case t: WavesIssueTransaction => t }

    val blockHeightByTransferId = blocks.flatMap(b => b.transactions.collect { case t: WavesTransferTransaction => t }.map(tx => tx.id -> b.height.get)).toMap

    val groupedByFrom = transfers.groupBy(_.from)
    val groupedByTo = transfers.groupBy(_.to)

    val registersF = Future.sequence(issues.map(i => assetsStore.registerAsset(Asset(i.id, i.name, "", i.decimals))))

    val observedFromF = fromAddressTransactionsStore.findObservables(transfers.map(_.from).toSet)
    val observedFromTransactionsF = observedFromF.map(_.flatMap(address => groupedByFrom(address)))

    val observedToF = toAddressTransactionsStore.findObservables(transfers.map(_.to).toSet)
    val observedToTransactionsF = observedToF.map(_.flatMap(address => groupedByTo(address)))

    val observedToBalancesF = balancesStore.findObservables(transfers.map(_.to).toSet ++ transfers.map(_.from).toSet)
    val observedToBalancesTransactionsF = observedToBalancesF.map(_.flatMap(address =>
      groupedByTo.getOrElse(address, Seq.empty).map(t => (t.to, t.assetId, t.amount, blockHeightByTransferId(t.id))) ++
        groupedByFrom.getOrElse(address, Seq.empty).map(t => (t.from, t.assetId, -t.amount - (if (t.feeAssetId.isEmpty) t.fee else 0), blockHeightByTransferId(t.id)))))

    val insertsF = for {
      observedFromTransactions <- observedFromTransactionsF
      observedToTransactions <- observedToTransactionsF
      observedToBalancesTransactions <- observedToBalancesTransactionsF
      fromTransacrionsOperationIds <- Future.sequence(observedFromTransactions
        .map(t => operationsStore.findOperationIdByTransactionId(t.id).map(r => t.id -> r)))
      toTransacrionsOperationIds <- Future.sequence(observedToTransactions
        .map(t => operationsStore.findOperationIdByTransactionId(t.id).map(r => t.id -> r)))
    } yield {
      val fromTransactionsOperationIdsMap = fromTransacrionsOperationIds.toMap
      val toTransactionsOperationIdsMap = toTransacrionsOperationIds.toMap
      Future.sequence(Seq(
        Future.sequence(observedFromTransactions.map(t => fromAddressTransactionsStore.addTransaction(
          Transaction(fromTransactionsOperationIdsMap(t.id), t.timestamp, t.from, t.to, t.assetId, t.amount, t.id, fromAddressTransactionsStore.field)))),
        Future.sequence(observedToTransactions.map(t => toAddressTransactionsStore.addTransaction(
          Transaction(toTransactionsOperationIdsMap(t.id), t.timestamp, t.from, t.to, t.assetId, t.amount, t.id, toAddressTransactionsStore.field))
        )),
        Future.sequence(observedToBalancesTransactions.map {
          case (address, assetId, amount, blockHeight) =>
            balancesStore.updateBalance(address, assetId.getOrElse(AssetsStore.WavesAsset.assetId), amount, blockHeight)
        })))
    }

    val operations = Future.sequence(Seq(registersF, insertsF))
    Await.result(operations, 5 minutes)
  })

  while (true) {
    logger.info(s"Checking new blocks was started")
    try {
      scanner.step() match {
        case Success(h) =>
          val lastProcessed = Await.result(stateStorage.readLastBlock, 1 minute)
          if (h > lastProcessed) {
            logger.info(s"Scanner '${scanner.toString}' step succeed, new confirmed height is $h now")
            Await.result(stateStorage.saveLastBlock(h), 1 minute)
          } else {
            logger.info(s"Scanner '${scanner.toString}' step succeed, but confirmed height is the same, sleep 30s")
            Thread.sleep(30000)
          }
        case Failure(f) =>
          logger.error(s"Scanner '${scanner.toString}' step failed: ${f.getMessage}, sleep 30s", f)
          Thread.sleep(30000)
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Unexpected error, sleep 30s", e)
        Thread.sleep(30000)
    }
    logger.info(s"Checking new blocks was finished")
  }
}
