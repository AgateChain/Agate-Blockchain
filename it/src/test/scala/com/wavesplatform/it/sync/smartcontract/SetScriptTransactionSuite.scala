package com.wavesplatform.it.sync.smartcontract

import com.wavesplatform.crypto
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.sync.{minFee, setScriptFee, transferAmount}
import com.wavesplatform.it.transactions.BaseTransactionSuite
import com.wavesplatform.it.util._
import com.wavesplatform.lang.v1.compiler.CompilerV1
import com.wavesplatform.lang.v1.parser.Parser
import com.wavesplatform.state._
import com.wavesplatform.transaction.Proofs
import com.wavesplatform.transaction.smart.SetScriptTransaction
import com.wavesplatform.transaction.smart.script.v1.ScriptV1
import com.wavesplatform.transaction.transfer._
import com.wavesplatform.utils.dummyCompilerContext
import org.scalatest.CancelAfterFailure
import play.api.libs.json.{JsNumber, Json}

class SetScriptTransactionSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val fourthAddress: String = sender.createAddress()

<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala
  private val acc0 = pkFromAddress(firstAddress)
  private val acc1 = pkFromAddress(secondAddress)
  private val acc2 = pkFromAddress(thirdAddress)
  private val acc3 = pkFromAddress(fourthAddress)

  private val transferAmount: Long = 1.Agate
  private val fee: Long            = 0.001.Agate
=======
  private val acc0 = pkByAddress(firstAddress)
  private val acc1 = pkByAddress(secondAddress)
  private val acc2 = pkByAddress(thirdAddress)
  private val acc3 = pkByAddress(fourthAddress)
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala

  test("setup acc0 with 1 Agate") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = sender.privateKey,
          recipient = acc0,
          amount = 3 * transferAmount + 3 * (0.00001.Agate + 0.00002.Agate), // Script fee
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = minFee,
          attachment = Array.emptyByteArray
        )
        .explicitGet()

    val transferId = sender
      .signedBroadcast(tx.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt)))
      .id
    nodes.waitForHeightAriseAndTxPresent(transferId)
  }

  test("set acc0 as 2of2 multisig") {
    val scriptText = {
      val untyped = Parser(s"""
        match tx {
          case t: Transaction => {
            let A = base58'${ByteStr(acc1.publicKey)}'
            let B = base58'${ByteStr(acc2.publicKey)}'
            let AC = sigVerify(tx.bodyBytes,tx.proofs[0],A)
            let BC = sigVerify(tx.bodyBytes,tx.proofs[1],B)
            AC && BC
          }
          case _ => false
        }

      """.stripMargin).get.value
      CompilerV1(dummyCompilerContext, untyped).explicitGet()._1
    }

    val script = ScriptV1(scriptText).explicitGet()
    val setScriptTransaction = SetScriptTransaction
      .selfSigned(SetScriptTransaction.supportedVersions.head, acc0, Some(script), setScriptFee, System.currentTimeMillis())
      .explicitGet()

    val setScriptId = sender
      .signedBroadcast(setScriptTransaction.json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(setScriptId)

    val acc0ScriptInfo = sender.addressScriptInfo(acc0.address)

    acc0ScriptInfo.script.isEmpty shouldBe false
    acc0ScriptInfo.scriptText.isEmpty shouldBe false
    acc0ScriptInfo.script.get.startsWith("base64:") shouldBe true

    val json = Json.parse(sender.get(s"/transactions/info/$setScriptId").getResponseBody)
    (json \ "script").as[String].startsWith("base64:") shouldBe true
  }

  test("can't send from acc0 using old pk") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala
          feeAmount = fee + 0.00001.Agate + 0.00002.Agate,
=======
          feeAmount = minFee + 0.00001.waves + 0.00002.waves,
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala
          attachment = Array.emptyByteArray
        )
        .explicitGet()
    assertBadRequest(sender.signedBroadcast(tx.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt))))
  }

  test("can send from acc0 using multisig of acc1 and acc2") {
    val unsigned =
      TransferTransactionV2
        .create(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala
          feeAmount = fee + 0.004.Agate,
=======
          feeAmount = minFee + 0.004.waves,
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala
          attachment = Array.emptyByteArray,
          proofs = Proofs.empty
        )
        .explicitGet()
    val sig1 = ByteStr(crypto.sign(acc1, unsigned.bodyBytes()))
    val sig2 = ByteStr(crypto.sign(acc2, unsigned.bodyBytes()))

    val signed = unsigned.copy(proofs = Proofs(Seq(sig1, sig2)))

    val versionedTransferId =
      sender.signedBroadcast(signed.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt))).id

    nodes.waitForHeightAriseAndTxPresent(versionedTransferId)
  }

  test("can clear script at acc0") {
    val unsigned = SetScriptTransaction
      .create(
        version = SetScriptTransaction.supportedVersions.head,
        sender = acc0,
        script = None,
<<<<<<< HEAD
<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala
        fee = fee + 0.004.Agate,
=======
        fee = minFee + 0.004.waves,
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala
=======
        fee = setScriptFee + 0.004.waves,
>>>>>>> 6726da31c7c56583f9ba835454f5bb9087c4b82b
        timestamp = System.currentTimeMillis(),
        proofs = Proofs.empty
      )
      .explicitGet()
    val sig1 = ByteStr(crypto.sign(acc1, unsigned.bodyBytes()))
    val sig2 = ByteStr(crypto.sign(acc2, unsigned.bodyBytes()))

    val signed = unsigned.copy(proofs = Proofs(Seq(sig1, sig2)))
    val clearScriptId = sender
      .signedBroadcast(signed.json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(clearScriptId)
  }

  test("can send using old pk of acc0") {
    val tx =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = acc0,
          recipient = acc3,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala
          feeAmount = fee + 0.004.Agate,
=======
          feeAmount = minFee + 0.004.waves,
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala
          attachment = Array.emptyByteArray
        )
        .explicitGet()
    val txId = sender.signedBroadcast(tx.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt))).id
    nodes.waitForHeightAriseAndTxPresent(txId)
  }
<<<<<<< HEAD:it/src/test/scala/com/wavesplatform/it/sync/SetScriptTransactionSuite.scala

  test("make masstransfer after some height") {
    val heightBefore = sender.height

    val scriptText = {
      val untyped = Parser(s"""
        let A = base58'${ByteStr(acc3.publicKey)}'

        let AC = sigVerify(tx.bodyBytes,tx.proof0,A)
        let heightVerification = if (height > $heightBefore + 10) then true else false

        AC && heightVerification
        """.stripMargin).get.value
      TypeChecker(dummyTypeCheckerContext, untyped).explicitGet()
    }

    val script = ScriptV1(scriptText).explicitGet()
    val setScriptTransaction = SetScriptTransaction
      .selfSigned(SetScriptTransaction.supportedVersions.head, acc0, Some(script), fee, System.currentTimeMillis())
      .explicitGet()

    val setScriptId = sender
      .signedBroadcast(setScriptTransaction.json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(setScriptId)

    sender.addressScriptInfo(firstAddress).scriptText.isEmpty shouldBe false

    val transfers =
      MassTransferTransaction.parseTransfersList(List(Transfer(thirdAddress, transferAmount), Transfer(secondAddress, transferAmount))).right.get

    val massTransferFee = 0.004.Agate + 0.0005.Agate * 4

    val unsigned =
      MassTransferTransaction
        .create(1, None, acc0, transfers, System.currentTimeMillis(), massTransferFee, Array.emptyByteArray, Proofs.empty)
        .explicitGet()

    val notarySig = ByteStr(crypto.sign(acc3, unsigned.bodyBytes()))

    val signed = unsigned.copy(proofs = Proofs(Seq(notarySig)))

    assertBadRequestAndResponse(sender.signedBroadcast(signed.json() + ("type" -> JsNumber(MassTransferTransaction.typeId.toInt))),
                                "Reason: TransactionNotAllowedByScript")

    sender.waitForHeight(heightBefore + 11, 2.minutes)

    val massTransferID = sender.signedBroadcast(signed.json() + ("type" -> JsNumber(MassTransferTransaction.typeId.toInt))).id

    nodes.waitForHeightAriseAndTxPresent(massTransferID)
  }
=======
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9:it/src/test/scala/com/wavesplatform/it/sync/smartcontract/SetScriptTransactionSuite.scala
}
