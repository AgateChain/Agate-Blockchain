package com.wavesplatform.matcher.market

import akka.actor.{Actor, Props}
import com.wavesplatform.matcher.MatcherSettings
import com.wavesplatform.matcher.market.OrderHistoryActor._
import com.wavesplatform.matcher.model.Events.{OrderAdded, OrderCanceled, OrderExecuted}
import com.wavesplatform.matcher.model._
import com.wavesplatform.metrics.TimerExt
import com.wavesplatform.state.ByteStr
import kamon.Kamon
import org.iq80.leveldb.DB

class OrderHistoryActor(db: DB, settings: MatcherSettings) extends Actor {

  val orderHistory = new OrderHistory(db, settings)

  private val timer          = Kamon.timer("matcher.order-history")
  private val addedTimer     = timer.refine("event" -> "added")
  private val executedTimer  = timer.refine("event" -> "executed")
  private val cancelledTimer = timer.refine("event" -> "cancelled")

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[OrderAdded])
    context.system.eventStream.subscribe(self, classOf[OrderExecuted])
    context.system.eventStream.subscribe(self, classOf[OrderCanceled])
  }

<<<<<<< HEAD
  def processExpirableRequest(r: Any): Unit = r match {
    case ValidateOrder(o, _) =>
      sender() ! ValidateOrderResult(o.id(), validateNewOrder(o))
    case GetTradableBalance(assetPair, addr, _) =>
      sender() ! getPairTradableBalance(assetPair, addr)
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
    case GetActiveOrdersByAddress(requestId, addr, assets, _) =>
      // Because all orders spend Agate for fee
      val wasAssetChanged: Option[AssetId] => Boolean = if (assets.contains(None)) { _ =>
        true
      } else assets.contains

      val allActiveOrders      = orderHistory.activeOrderIdsByAddress(addr.stringRepr)
      val activeOrdersByAssets = allActiveOrders.collect { case (assetId, id) if wasAssetChanged(assetId) => id -> orderHistory.orderInfo(id) }

      val active: Seq[LimitOrder] = activeOrdersByAssets.flatMap {
        case (id, info) =>
          orderHistory.order(id).map { order =>
            LimitOrder(order).partial(info.remaining)
          }
      }(collection.breakOut)

      sender().forward(GetActiveOrdersByAddressResponse(requestId, addr, active))
    case GetOpenPortfolio(addr, _) =>
      sender() ! GetOpenPortfolioResponse(OpenPortfolio(orderHistory.openPortfolio(addr).orders.filter(_._2 > 0)))
=======
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9
=======
    case DeleteOrderFromHistory(_, address, maybeId, _) =>
      val result = for {
        id <- maybeId.toRight[MatcherResponse](NotImplemented("Batch order deletion is not supported yet"))
        _ <- orderHistory.deleteOrder(address, id).left.map[MatcherResponse] {
          case LimitOrder.NotFound => StatusCodes.NotFound   -> s"Order $id not found"
          case other               => StatusCodes.BadRequest -> s"Invalid status: $other"
        }
      } yield id

      sender() ! result.fold[MatcherResponse](identity, id => OrderDeleted(id))
>>>>>>> 272596caeb0136d9fabc50602889b0e4694cdd76
=======
>>>>>>> 6726da31c7c56583f9ba835454f5bb9087c4b82b
  }

=======
>>>>>>> 501f3836ad1f1aadb0f0a7ee82c490cb3425da1f
  override def receive: Receive = {
    case ev: OrderAdded =>
      addedTimer.measure(orderHistory.process(ev))
    case ev: OrderExecuted =>
      executedTimer.measure(orderHistory.process(ev))
    case ev: OrderCanceled =>
      cancelledTimer.measure(orderHistory.process(ev))
    case ForceCancelOrderFromHistory(id) =>
      forceCancelOrder(id)
  }

  def forceCancelOrder(id: ByteStr): Unit = {
    val maybeOrder = orderHistory.order(id)
    for (o <- maybeOrder) {
      val oi = orderHistory.orderInfo(id)
      orderHistory.process(OrderCanceled(LimitOrder.limitOrder(o.price, oi.remaining, oi.remainingFee, o), unmatchable = false))
    }
    sender ! maybeOrder
  }
}

object OrderHistoryActor {
  def name: String = "OrderHistory"

  def props(db: DB, settings: MatcherSettings): Props = Props(new OrderHistoryActor(db, settings))

  case class ForceCancelOrderFromHistory(orderId: ByteStr)
}
