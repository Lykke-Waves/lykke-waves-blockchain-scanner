package ru.tolsi.lykke.waves.blockchainscanner

import com.mongodb.casbah.{MongoClient, MongoCollection}
import com.typesafe.scalalogging.StrictLogging
import ru.tolsi.lykke.common.repository.mongo._
import ru.tolsi.lykke.common.repository.{Asset, AssetsStore, Transaction}
import ru.tolsi.lykke.waves.blockchainscanner.storage.MongoStateStorage

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

object Server extends App with StrictLogging {
  private val mongoClient = MongoClient()
  private val db = mongoClient.getDB("lykke-waves")
  private val stateStorage = new MongoStateStorage(new MongoCollection(db.getCollection("processed")))
  private val from = Await.result(stateStorage.readLastBlock, 1 minute)
  private val api = new WavesApi("https://nodes.wavesnodes.com")

  private val assetsStore = new MongoAssetsStore(new MongoCollection(db.getCollection("assets")))
  private val balancesStore = new MongoBalancesStore(new MongoCollection(db.getCollection("balances")),
    new MongoCollection(db.getCollection("balances_observations")))
  private val fromAddressTransactionsStore = new MongoFromAddressTransactionsStore(new MongoCollection(db.getCollection("from_address_transactions")),
    new MongoCollection(db.getCollection("from_address_transactions_observations")))
  private val toAddressTransactionsStore = new MongoToAddressTransactionsStore(new MongoCollection(db.getCollection("to_address_transactions")),
    new MongoCollection(db.getCollection("to_address_transactions_observations")))

  new WavesBlockScanner(from, by = 99, api, confirmations = 20, blocks => {
    // todo retries

    val transactions = blocks.flatMap(_.transactions)

    val transfers = transactions.collect { case t: WavesTransferTransaction => t }
    val issues = transactions.collect { case t: WavesIssueTransaction => t }

    val groupedByFrom = transfers.groupBy(_.from)
    val groupedByTo = transfers.groupBy(_.to)

    val registersF = Future.sequence(issues.map(i => assetsStore.registerAsset(Asset(i.id, i.name, "", i.decimals))))

    val observedFromF = fromAddressTransactionsStore.findObservables(transfers.map(_.from).toSet)
    val observedFromTransactionsF = observedFromF.map(_.flatMap(address => groupedByFrom(address)))
    val observedToF = toAddressTransactionsStore.findObservables(transfers.map(_.to).toSet)
    val observedToTransactionsF = observedToF.map(_.flatMap(address => groupedByTo(address)))
    val observedToBalancesF = balancesStore.findObservables(transfers.map(_.to).toSet)
    val observedToBalancesTransactionsF = observedToBalancesF.map(_.flatMap(address =>
      groupedByTo.getOrElse(address, Seq.empty).map(t => (t.to, t.assetId, t.amount)) ++
        groupedByFrom.getOrElse(address, Seq.empty).map(t => (t.from, t.assetId, -t.amount))))

    val insertsF = for {
      observedFromTransactions <- observedFromTransactionsF
      observedToTransactions <- observedToTransactionsF
      observedToBalancesTransactions <- observedToBalancesTransactionsF
    } yield {
      Future.sequence(Seq(
        // todo operationId
        Future.sequence(observedFromTransactions.map(t => fromAddressTransactionsStore.addTransaction(
          Transaction(None, t.timestamp, t.from, t.to, t.assetId, t.amount, t.id, fromAddressTransactionsStore.field)))),
        Future.sequence(observedToTransactions.map(t => toAddressTransactionsStore.addTransaction(
          Transaction(None, t.timestamp, t.from, t.to, t.assetId, t.amount, t.id, toAddressTransactionsStore.field))
        )),
        Future.sequence(observedToBalancesTransactions.map {
          case (address, assetId, amount) =>
            // todo block h?!
            balancesStore.updateBalance(address, assetId.getOrElse(AssetsStore.WavesAsset.assetId), amount, 0)
        })))
    }

    val operations = Future.sequence(Seq(registersF, insertsF))
    Await.result(operations, 5 minutes)
  })
}
