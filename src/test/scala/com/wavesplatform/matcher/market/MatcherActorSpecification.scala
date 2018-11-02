package com.wavesplatform.matcher.market

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorRef, Kill, Props, Terminated}
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import com.wavesplatform.account.PrivateKeyAccount
import com.wavesplatform.matcher.MatcherTestData
import com.wavesplatform.matcher.api.OrderAccepted
import com.wavesplatform.matcher.market.MatcherActor.{GetMarkets, MarketData}
import com.wavesplatform.matcher.market.MatcherActorSpecification.FailAtStartActor
import com.wavesplatform.matcher.model.ExchangeTransactionCreator
import com.wavesplatform.state.{AssetDescription, Blockchain, ByteStr}
import com.wavesplatform.transaction.AssetId
import com.wavesplatform.transaction.assets.exchange.AssetPair
import com.wavesplatform.utils.randomBytes
import com.wavesplatform.utx.UtxPool
import io.netty.channel.group.ChannelGroup
import org.scalamock.scalatest.PathMockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually

class MatcherActorSpecification
    extends MatcherSpec("MatcherActor")
    with MatcherTestData
    with BeforeAndAfterEach
    with PathMockFactory
    with ImplicitSender
    with Eventually {

  private val blockchain: Blockchain = stub[Blockchain]
  (blockchain.assetDescription _)
    .when(*)
    .returns(Some(AssetDescription(PrivateKeyAccount(Array.empty), "Unknown".getBytes, Array.emptyByteArray, 8, reissuable = false, 1, None, 0)))
    .anyNumberOfTimes()

  "MatcherActor" should {
    "return all open markets" in {
      val actor = defaultActor()

      val pair  = AssetPair(randomAssetId, randomAssetId)
      val order = buy(pair, 2000, 1)

      (blockchain.accountScript _)
        .when(order.sender.toAddress)
        .returns(None)

      (blockchain.accountScript _)
        .when(order.matcherPublicKey.toAddress)
        .returns(None)

      actor ! order
      expectMsg(OrderAccepted(order))

      actor ! GetMarkets

      expectMsgPF() {
        case s @ Seq(MarketData(_, "Unknown", "Unknown", _, _, _)) =>
          s.size shouldBe 1
      }
    }
<<<<<<< HEAD
<<<<<<< HEAD
  }

  "GetMarketsResponse" should {
    "serialize to json" in {
      val Agate  = "Agate"
      val a1Name = "BITCOIN"
      val a1     = strToSomeAssetId(a1Name)

      val a2Name = "US DOLLAR"
      val a2     = strToSomeAssetId(a2Name)

      val pair1 = AssetPair(a1, None)
      val pair2 = AssetPair(a1, a2)

      val now = NTP.correctedTime()
      val json =
        GetMarketsResponse(Array(), Seq(MarketData(pair1, a1Name, Agate, now, None, None), MarketData(pair2, a1Name, a2Name, now, None, None))).json

      ((json \ "markets")(0) \ "priceAsset").as[String] shouldBe AssetPair.WavesName
      ((json \ "markets")(0) \ "priceAssetName").as[String] shouldBe Agate
      ((json \ "markets")(0) \ "amountAsset").as[String] shouldBe a1.get.base58
      ((json \ "markets")(0) \ "amountAssetName").as[String] shouldBe a1Name
      ((json \ "markets")(0) \ "created").as[Long] shouldBe now

      ((json \ "markets")(1) \ "amountAssetName").as[String] shouldBe a1Name
    }
=======
=======

    "mark an order book as failed" when {
      "it crashes at start" in {
        val pair = AssetPair(randomAssetId, randomAssetId)
        val ob   = emptyOrderBookRefs
        val actor = waitInitialization(
          TestActorRef(
            new MatcherActor(
              ob,
              (_, _) => Props(classOf[FailAtStartActor], pair),
              blockchain.assetDescription
            )
          ))

        actor ! buy(pair, 2000, 1)
        eventually { ob.get()(pair) shouldBe 'left }
      }

      "it crashes during the work" in {
        val ob    = emptyOrderBookRefs
        val actor = defaultActor(ob)

        val a1, a2, a3 = randomAssetId

        val pair1  = AssetPair(a1, a2)
        val order1 = buy(pair1, 2000, 1)

        val pair2  = AssetPair(a2, a3)
        val order2 = buy(pair2, 2000, 1)

        actor ! order1
        actor ! order2
        receiveN(2)

        ob.get()(pair1) shouldBe 'right
        ob.get()(pair2) shouldBe 'right

        val toKill = actor.getChild(List(OrderBookActor.name(pair1)).iterator)

        val probe = TestProbe()
        probe.watch(toKill)
        toKill.tell(Kill, actor)
        probe.expectMsgType[Terminated]

        ob.get()(pair1) shouldBe 'left
      }
    }

>>>>>>> 1a6b3243dad151498c2106ff6f09c27303a5800f
    "delete order books" is pending
    "forward new orders to order books" is pending
>>>>>>> 501f3836ad1f1aadb0f0a7ee82c490cb3425da1f
  }

  private def defaultActor(ob: AtomicReference[Map[AssetPair, Either[Unit, ActorRef]]] = emptyOrderBookRefs): TestActorRef[MatcherActor] = {
    val txFactory = new ExchangeTransactionCreator(MatcherAccount, matcherSettings, ntpTime).createTransaction _
    waitInitialization(
      TestActorRef(
        new MatcherActor(
          ob,
          (assetPair, matcher) =>
            OrderBookActor.props(matcher, assetPair, _ => {}, _ => {}, mock[UtxPool], mock[ChannelGroup], matcherSettings, txFactory),
          blockchain.assetDescription
        )
      ))
  }

  private def waitInitialization(x: TestActorRef[MatcherActor]): TestActorRef[MatcherActor] = eventually {
    x.underlyingActor.recoveryFinished shouldBe true
    x
  }
  private def emptyOrderBookRefs             = new AtomicReference(Map.empty[AssetPair, Either[Unit, ActorRef]])
  private def randomAssetId: Option[AssetId] = Some(ByteStr(randomBytes()))
}

object MatcherActorSpecification {
  private class FailAtStartActor(pair: AssetPair) extends Actor {
    throw new RuntimeException("I don't want to work today")
    override def receive: Receive = Actor.emptyBehavior
  }
}
