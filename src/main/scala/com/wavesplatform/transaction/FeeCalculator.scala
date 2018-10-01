package com.wavesplatform.transaction

import com.wavesplatform.settings.FunctionalitySettings
import com.wavesplatform.state._
import com.wavesplatform.transaction.FeeCalculator._
import com.wavesplatform.transaction.ValidationError.InsufficientFee
import com.wavesplatform.transaction.assets._
import com.wavesplatform.transaction.assets.exchange.ExchangeTransaction
import com.wavesplatform.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import com.wavesplatform.transaction.smart.SetScriptTransaction
import com.wavesplatform.transaction.transfer._

class FeeCalculator(blockchain: Blockchain) {

  private val Kb = 1024

<<<<<<< HEAD
  private val map: Map[String, Long] = {
    settings.fees.flatMap { fs =>
      val transactionType = fs._1
      fs._2.map { v =>
<<<<<<< HEAD:src/main/scala/scorex/transaction/FeeCalculator.scala
        val maybeAsset = if (v.asset.toUpperCase == "AGATE") None else ByteStr.decodeBase58(v.asset).toOption
=======
        val maybeAsset = if (v.asset.toUpperCase == "WAVES") None else Some(ByteStr.decodeBase58(v.asset).get)
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:src/main/scala/com/wavesplatform/transaction/FeeCalculator.scala
        val fee        = v.fee

        TransactionAssetFee(transactionType, maybeAsset).key -> fee
      }
    }
  }

=======
>>>>>>> 272596caeb0136d9fabc50602889b0e4694cdd76
  def enoughFee[T <: Transaction](tx: T, blockchain: Blockchain, fs: FunctionalitySettings): Either[ValidationError, T] =
    if (blockchain.height >= Sponsorship.sponsoredFeesSwitchHeight(blockchain, fs)) Right(tx)
    else enoughFee(tx)

  def enoughFee[T <: Transaction](tx: T): Either[ValidationError, T] = {
    val (txFeeAssetId, txFeeValue) = tx.assetFee
<<<<<<< HEAD
    val txAssetFeeKey              = TransactionAssetFee(tx.builder.typeId, txFeeAssetId).key
    for {
      txMinBaseFee <- Either.cond(map.contains(txAssetFeeKey), map(txAssetFeeKey), GenericError(s"Minimum fee is not defined for $txAssetFeeKey"))
      minTxFee = minFeeFor(tx, txFeeAssetId, txMinBaseFee)
      _ <- Either.cond(
        txFeeValue >= minTxFee,
        (),
        GenericError {
          s"Fee in ${txFeeAssetId.fold("Agate")(_.toString)} for ${tx.builder.classTag} transaction does not exceed minimal value of $minTxFee"
        }
      )
    } yield tx
=======
    val minFeeForTx                = minFeeFor(tx)
    txFeeAssetId match {
      case None =>
        Either
          .cond(
            txFeeValue >= minFeeForTx,
            tx,
            InsufficientFee(s"Fee for ${tx.builder.classTag} transaction does not exceed minimal value of $minFeeForTx")
          )
      case Some(_) => Right(tx)
    }
>>>>>>> 272596caeb0136d9fabc50602889b0e4694cdd76
  }

  private def minFeeFor(tx: Transaction): Long = {
    val baseFee = FeeConstants(tx.builder.typeId)
    tx match {
      case tx: DataTransaction =>
        val sizeInKb = 1 + (tx.bytes().length - 1) / Kb
        baseFee * sizeInKb
      case tx: MassTransferTransaction =>
        val transferFee = FeeConstants(TransferTransactionV1.typeId)
        transferFee + baseFee * tx.transfers.size
      case _ => baseFee
    }
  }
}

object FeeCalculator {
  val FeeConstants = Map(
    PaymentTransaction.typeId      -> 100000,
    IssueTransaction.typeId        -> 100000000,
    TransferTransaction.typeId     -> 100000,
    MassTransferTransaction.typeId -> 50000,
    ReissueTransaction.typeId      -> 100000,
    BurnTransaction.typeId         -> 100000,
    ExchangeTransaction.typeId     -> 300000,
    LeaseTransaction.typeId        -> 100000,
    LeaseCancelTransaction.typeId  -> 100000,
    CreateAliasTransaction.typeId  -> 100000,
    DataTransaction.typeId         -> 100000,
    SetScriptTransaction.typeId    -> 100000,
    SponsorFeeTransaction.typeId   -> 100000000
  )
}
