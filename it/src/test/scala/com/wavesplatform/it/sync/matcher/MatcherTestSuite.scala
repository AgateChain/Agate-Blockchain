package com.wavesplatform.it.sync.matcher

import com.typesafe.config.Config
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.api.SyncMatcherHttpApi._
import com.wavesplatform.it.api.{AssetDecimalsInfo, LevelResponse}
import com.wavesplatform.it.matcher.MatcherSuiteBase
import com.wavesplatform.it.sync._
import com.wavesplatform.it.sync.matcher.config.MatcherDefaultConfig._
import com.wavesplatform.it.util._
import com.wavesplatform.state.ByteStr
import com.wavesplatform.transaction.assets.exchange.OrderType._
import com.wavesplatform.transaction.assets.exchange.{AssetPair, _}

import scala.concurrent.duration._
import scala.util.Random

class MatcherTestSuite extends MatcherSuiteBase {
  private val aliceSellAmount                     = 500
  private val exTxFee                             = 300000
  private val amountAssetName                     = "AliceCoin"
  private val AssetQuantity                       = 1000
  private val aliceCoinDecimals: Byte             = 0
  override protected def nodeConfigs: Seq[Config] = Configs

  private def orderVersion = (Random.nextInt(2) + 1).toByte

  "Check cross ordering between Alice and Bob" - {
    // Alice issues new asset
    val aliceAsset = aliceNode
      .issue(aliceAcc.address, amountAssetName, "AliceCoin for matcher's tests", AssetQuantity, aliceCoinDecimals, reissuable = false, issueFee, 2)
      .id
    nodes.waitForHeightAriseAndTxPresent(aliceAsset)

    val aliceWavesPair = AssetPair(ByteStr.decodeBase58(aliceAsset).toOption, None)

    val order1         = matcherNode.prepareOrder(aliceAcc, aliceWavesPair, SELL, aliceSellAmount, 2000.waves, version = orderVersion, timeToLive = 2.minutes)
    val order1Response = matcherNode.placeOrder(order1)

    "can't place an order with the same timestamp" in {
      val rawOrder2 = order1 match {
        case x: OrderV1 => x.copy(amount = x.amount + 1)
        case x: OrderV2 => x.copy(amount = x.amount + 1)
      }

      val order2 = Order.sign(rawOrder2, aliceAcc)
      matcherNode.expectIncorrectOrderPlacement(order2, 400, "OrderRejected") shouldBe true
    }

    "assert addresses balances" in {
      aliceNode.assertAssetBalance(aliceAcc.address, aliceAsset, AssetQuantity)
      matcherNode.assertAssetBalance(matcherAcc.address, aliceAsset, 0)
      bobNode.assertAssetBalance(bobAcc.address, aliceAsset, 0)
    }

    "matcher should respond with Public key" in {
      matcherNode.matcherGet("/matcher").getResponseBody.stripPrefix("\"").stripSuffix("\"") shouldBe matcherNode.publicKeyStr
    }

    "get opened trading markets" in {
      val openMarkets = matcherNode.tradingMarkets()
      openMarkets.markets.size shouldBe 1
      val markets = openMarkets.markets.head

      markets.amountAssetName shouldBe amountAssetName
      markets.amountAssetInfo shouldBe Some(AssetDecimalsInfo(aliceCoinDecimals))

      markets.priceAssetName shouldBe "WAVES"
      markets.priceAssetInfo shouldBe Some(AssetDecimalsInfo(8))
    }

    "sell order could be placed correctly" - {
      "alice places sell order" in {
        order1Response.status shouldBe "OrderAccepted"

        // Alice checks that the order in order book
        matcherNode.waitOrderStatus(aliceWavesPair, order1Response.message.id, "Accepted")

        // Alice check that order is correct
        val orders = matcherNode.orderBook(aliceWavesPair)
        orders.asks.head.amount shouldBe aliceSellAmount
        orders.asks.head.price shouldBe 2000.waves
      }

      "frozen amount should be listed via matcherBalance REST endpoint" in {
        matcherNode.reservedBalance(aliceAcc) shouldBe Map(aliceAsset -> aliceSellAmount)

        matcherNode.reservedBalance(bobAcc) shouldBe Map()
      }

      "and should be listed by trader's publiс key via REST" in {
        matcherNode.fullOrderHistory(aliceAcc).map(_.id) should contain(order1Response.message.id)
      }

      "and should match with buy order" in {
        val bobBalance     = matcherNode.accountBalances(bobAcc.address)._1
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = matcherNode.accountBalances(aliceAcc.address)._1

        // Bob places a buy order
        val order2 = matcherNode.placeOrder(bobAcc, aliceWavesPair, BUY, 200, 2.waves * Order.PriceConstant, matcherFee, orderVersion)
        order2.status shouldBe "OrderAccepted"

        matcherNode.waitOrderStatus(aliceWavesPair, order1Response.message.id, "PartiallyFilled")
        matcherNode.waitOrderStatus(aliceWavesPair, order2.message.id, "Filled")

        matcherNode.orderHistoryByPair(bobAcc, aliceWavesPair).map(_.id) should contain(order2.message.id)
        matcherNode.fullOrderHistory(bobAcc).map(_.id) should contain(order2.message.id)

        matcherNode.waitOrderInBlockchain(order2.message.id)

        // Bob checks that asset on his balance
        matcherNode.assertAssetBalance(bobAcc.address, aliceAsset, 200)

        // Alice checks that part of her order still in the order book
        val orders = matcherNode.orderBook(aliceWavesPair)
        orders.asks.head.amount shouldBe 300
        orders.asks.head.price shouldBe 2000.waves

        // Alice checks that she sold some assets
        matcherNode.assertAssetBalance(aliceAcc.address, aliceAsset, 800)

        // Bob checks that he spent some Waves
        val updatedBobBalance = matcherNode.accountBalances(bobAcc.address)._1
        updatedBobBalance shouldBe (bobBalance - 2000 * 200 - matcherFee)

        // Alice checks that she received some Waves
        val updatedAliceBalance = matcherNode.accountBalances(aliceAcc.address)._1
        updatedAliceBalance shouldBe (aliceBalance + 2000 * 200 - (matcherFee * 200.0 / 500.0).toLong)

        // Matcher checks that it earn fees
        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance shouldBe (matcherBalance + matcherFee + (matcherFee * 200.0 / 500.0).toLong - exTxFee)
      }

      "request activeOnly orders" in {
        val aliceOrders = matcherNode.activeOrderHistory(aliceAcc)
        aliceOrders.map(_.id) shouldBe Seq(order1Response.message.id)
        val bobOrders = matcherNode.activeOrderHistory(bobAcc)
        bobOrders.map(_.id) shouldBe Seq()
      }

      "submitting sell orders should check availability of asset" in {
        // Bob trying to place order on more assets than he has - order rejected
        val badOrder = matcherNode.prepareOrder(bobAcc, aliceWavesPair, SELL, 300, 1900.waves, orderVersion)
        matcherNode.expectIncorrectOrderPlacement(badOrder, 400, "OrderRejected") should be(true)

        // Bob places order on available amount of assets - order accepted
        val order3 = matcherNode.placeOrder(bobAcc, aliceWavesPair, SELL, 150, 1900.waves, matcherFee, orderVersion)
        matcherNode.waitOrderStatus(aliceWavesPair, order3.message.id, "Accepted")

        // Bob checks that the order in the order book
        val orders = matcherNode.orderBook(aliceWavesPair)
        orders.asks should contain(LevelResponse(150, 1900.waves))
      }

      "buy order should match on few price levels" in {
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = matcherNode.accountBalances(aliceAcc.address)._1
        val bobBalance     = matcherNode.accountBalances(bobAcc.address)._1

        // Alice places a buy order
        val order4 =
          matcherNode.placeOrder(aliceAcc, aliceWavesPair, BUY, 350, (21.waves / 10.0 * Order.PriceConstant).toLong, matcherFee, orderVersion)
        order4.status should be("OrderAccepted")

        // Where were 2 sells that should fulfill placed order
        matcherNode.waitOrderStatus(aliceWavesPair, order4.message.id, "Filled")

        // Check balances
        matcherNode.waitOrderInBlockchain(order4.message.id)
        matcherNode.assertAssetBalance(aliceAcc.address, aliceAsset, 950)
        matcherNode.assertAssetBalance(bobAcc.address, aliceAsset, 50)

        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance should be(
          matcherBalance - 2 * exTxFee + matcherFee + (matcherFee * 150.0 / 350.0).toLong + (matcherFee * 200.0 / 350.0).toLong + (matcherFee * 200.0 / 500.0).toLong)

        val updatedBobBalance = matcherNode.accountBalances(bobAcc.address)._1
        updatedBobBalance should be(bobBalance - matcherFee + 150 * 1900)

        val updatedAliceBalance = matcherNode.accountBalances(aliceAcc.address)._1
        updatedAliceBalance should be(
          aliceBalance - (matcherFee * 200.0 / 350.0).toLong - (matcherFee * 150.0 / 350.0).toLong - (matcherFee * 200.0 / 500.0).toLong - 1900 * 150)
      }

      "order could be canceled and resubmitted again" in {
        // Alice cancels the very first order (100 left)
        val status1 = matcherNode.cancelOrder(aliceAcc, aliceWavesPair, order1Response.message.id)
        status1.status should be("OrderCanceled")

        // Alice checks that the order book is empty
        val orders1 = matcherNode.orderBook(aliceWavesPair)
        orders1.asks.size should be(0)
        orders1.bids.size should be(0)

        // Alice places a new sell order on 100
        val order4 = matcherNode.placeOrder(aliceAcc, aliceWavesPair, SELL, 100, 2000.waves, matcherFee, orderVersion)
        order4.status should be("OrderAccepted")

        // Alice checks that the order is in the order book
        val orders2 = matcherNode.orderBook(aliceWavesPair)
        orders2.asks should contain(LevelResponse(100, 2000.waves))
      }

      "buy order should execute all open orders and put remaining in order book" in {
        val matcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        val aliceBalance   = matcherNode.accountBalances(aliceAcc.address)._1
        val bobBalance     = matcherNode.accountBalances(bobAcc.address)._1

        // Bob places buy order on amount bigger then left in sell orders
        val order5 = matcherNode.placeOrder(bobAcc, aliceWavesPair, BUY, 130, 2000.waves, matcherFee, orderVersion)

        // Check that the order is partially filled
        matcherNode.waitOrderStatus(aliceWavesPair, order5.message.id, "PartiallyFilled")

        // Check that remaining part of the order is in the order book
        val orders = matcherNode.orderBook(aliceWavesPair)
        orders.bids should contain(LevelResponse(30, 2000.waves))

        // Check balances
        matcherNode.waitOrderInBlockchain(order5.message.id)
        matcherNode.assertAssetBalance(aliceAcc.address, aliceAsset, 850)
        matcherNode.assertAssetBalance(bobAcc.address, aliceAsset, 150)

        val updatedMatcherBalance = matcherNode.accountBalances(matcherNode.address)._1
        updatedMatcherBalance should be(matcherBalance - exTxFee + matcherFee + (matcherFee * 100.0 / 130.0).toLong)

        val updatedBobBalance = matcherNode.accountBalances(bobAcc.address)._1
        updatedBobBalance should be(bobBalance - (matcherFee * 100.0 / 130.0).toLong - 100 * 2000)

        val updatedAliceBalance = matcherNode.accountBalances(aliceAcc.address)._1
        updatedAliceBalance should be(aliceBalance - matcherFee + 2000 * 100)
      }

      "market status" in {
        val resp = matcherNode.marketStatus(aliceWavesPair)

        resp.lastPrice shouldBe Some(2000.waves)
        resp.lastSide shouldBe Some("buy") // Same type as order5
        resp.bid shouldBe Some(2000.waves)
        resp.bidAmount shouldBe Some(30)
        resp.ask shouldBe None
        resp.askAmount shouldBe None
      }

      "request order book for blacklisted pair" in {
        val f = matcherNode.matcherGetStatusCode(s"/matcher/orderbook/$ForbiddenAssetId/WAVES", 404)
        f.message shouldBe s"Invalid Asset ID: $ForbiddenAssetId"
      }

      "should consider UTX pool when checking the balance" in {
        // Bob issues new asset
        val bobAssetQuantity = 10000
        val bobAssetName     = "BobCoin"
        val bobAsset         = bobNode.issue(bobAcc.address, bobAssetName, "Bob's asset", bobAssetQuantity, 0, reissuable = false, issueFee, 2).id
        matcherNode.waitForTransaction(bobAsset)

        matcherNode.assertAssetBalance(aliceAcc.address, bobAsset, 0)
        matcherNode.assertAssetBalance(matcherAcc.address, bobAsset, 0)
        matcherNode.assertAssetBalance(bobAcc.address, bobAsset, bobAssetQuantity)
        val bobWavesPair = AssetPair(ByteStr.decodeBase58(bobAsset).toOption, None)

        def bobOrder = matcherNode.prepareOrder(bobAcc, bobWavesPair, SELL, bobAssetQuantity, 1.waves, matcherFee, orderVersion)

        val order6 = matcherNode.placeOrder(bobOrder)
        matcherNode.waitOrderStatus(bobWavesPair, order6.message.id, "Accepted")

        // Alice wants to buy all Bob's assets for 1 Wave
        val order7 = matcherNode.placeOrder(aliceAcc, bobWavesPair, BUY, bobAssetQuantity, 1.waves, matcherFee, orderVersion)
        matcherNode.waitOrderStatus(bobWavesPair, order7.message.id, "Filled")

        // Bob tries to do the same operation, but at now he have no assets
        matcherNode.expectIncorrectOrderPlacement(bobOrder, 400, "OrderRejected")
      }

      "trader can buy waves for assets with order without having waves" in {
        // Bob issues new asset
        val bobAssetQuantity = 10000
        val bobAssetName     = "BobCoin2"
        val bobAsset         = bobNode.issue(bobAcc.address, bobAssetName, "Bob's asset", bobAssetQuantity, 0, reissuable = false, issueFee, 2).id
        nodes.waitForHeightAriseAndTxPresent(bobAsset)

        val bobWavesPair = AssetPair(
          amountAsset = ByteStr.decodeBase58(bobAsset).toOption,
          priceAsset = None
        )

        matcherNode.assertAssetBalance(aliceAcc.address, bobAsset, 0)
        matcherNode.assertAssetBalance(matcherAcc.address, bobAsset, 0)
        matcherNode.assertAssetBalance(bobAcc.address, bobAsset, bobAssetQuantity)

        // Bob wants to sell all own assets for 1 Wave
        def bobOrder = matcherNode.prepareOrder(bobAcc, bobWavesPair, SELL, bobAssetQuantity, 1.waves * Order.PriceConstant, matcherFee, orderVersion)

        val order8 = matcherNode.placeOrder(bobOrder)
        matcherNode.waitOrderStatus(bobWavesPair, order8.message.id, "Accepted")

        // Bob moves all waves to Alice
        val h1              = matcherNode.height
        val bobBalance      = matcherNode.accountBalances(bobAcc.address)._1
        val transferAmount  = bobBalance - minFee
        val transferAliceId = bobNode.transfer(bobAcc.address, aliceAcc.address, transferAmount, minFee, None, None, 2).id
        nodes.waitForHeightAriseAndTxPresent(transferAliceId)

        matcherNode.accountBalances(bobAcc.address)._1 shouldBe 0

        // Order should stay accepted
        matcherNode.waitForHeight(h1 + 5, 2.minutes)
        matcherNode.waitOrderStatus(bobWavesPair, order8.message.id, "Accepted")

        // Cleanup
        matcherNode.cancelOrder(bobAcc, bobWavesPair, order8.message.id).status should be("OrderCanceled")
        val transferBobId = aliceNode.transfer(aliceAcc.address, bobAcc.address, transferAmount, minFee, None, None, 2).id
        nodes.waitForHeightAriseAndTxPresent(transferBobId)
      }
    }
  }
}
