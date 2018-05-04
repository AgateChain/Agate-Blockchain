package com.wavesplatform.it.sync

import com.wavesplatform.crypto
import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.transactions.BaseTransactionSuite
import com.wavesplatform.it.util._
import com.wavesplatform.lang.v1.{Parser, TypeChecker}
import com.wavesplatform.state._
import com.wavesplatform.utils.dummyTypeCheckerContext
import org.scalatest.CancelAfterFailure
import play.api.libs.json.JsNumber
import scorex.account.PrivateKeyAccount
import scorex.transaction.{Proofs}
import scorex.transaction.smart.SetScriptTransaction
import scorex.transaction.smart.script.v1.ScriptV1
import scorex.transaction.transfer._
import scorex.crypto.encode.{Base58 => ScorexBase58}

class SmartContractsAtomicSwapSuite extends BaseTransactionSuite with CancelAfterFailure {

  private val BobBC1: String   = sender.createAddress()
  private val AliceBC1: String = sender.createAddress()
  private val swapBC1: String  = sender.createAddress()

  private val BobBC2: String   = sender.createAddress()
  private val AliceBC2: String = sender.createAddress()
  private val swapBC2: String  = sender.createAddress()

  private val transferAmount: Long = 1.waves
  private val fee: Long            = 0.001.waves

  private val secretText = "some secret message from Alice"
  private val secretHash = ScorexBase58.encode(secretText.getBytes)

  private val sc1 = {
    val untyped = Parser(s"""
    true
      """.stripMargin).get.value
    TypeChecker(dummyTypeCheckerContext, untyped).explicitGet()
  }

  private val sc2 = {
    val untyped = Parser(s"""
    let Alice = extract(addressFromString("${AliceBC2}")).bytes
    let backToBob = extract(addressFromString("${BobBC2}")).bytes
    let txRecipient = addressFromRecipient(tx.recipient).bytes

    let txTransfer = if((txRecipient == Alice) && (sha256(tx.proof0) == base58'BN6RTYGWcwektQfSFzH8raYo9awaLgQ7pLyWLQY4S4F5') && (100 >= height)) then true else false
    let backToBobAfterHeight = if ((height >= 101) && (txRecipient == txToBob)) then true else false

    txTransfer || backToBobAfterHeight
      """.stripMargin).get.value
    TypeChecker(dummyTypeCheckerContext, untyped).explicitGet()
  }

  test("step0: balances initialization") {
    val toAliceBC1TxId = sender.transfer(sender.address, AliceBC1, 10 * transferAmount, fee).id
    nodes.waitForHeightAriseAndTxPresent(toAliceBC1TxId)

    val toSwapBC1TxId = sender.transfer(sender.address, swapBC1, transferAmount, fee).id
    nodes.waitForHeightAriseAndTxPresent(toSwapBC1TxId)

    val toBobBC2TxId = sender.transfer(sender.address, BobBC2, 10 * transferAmount, fee).id
    nodes.waitForHeightAriseAndTxPresent(toBobBC2TxId)

    val toSwapBC2TxId = sender.transfer(sender.address, swapBC2, transferAmount, fee).id
    nodes.waitForHeightAriseAndTxPresent(toSwapBC2TxId)
  }

  test("step1: create and setup smart contract SC1") {
    val pkSwapBC1 = PrivateKeyAccount.fromSeed(sender.seed(swapBC1)).right.get
    val script    = ScriptV1(sc1).explicitGet()
    val sc1SetTx = SetScriptTransaction
      .selfSigned(version = SetScriptTransaction.supportedVersions.head,
                  sender = pkSwapBC1,
                  script = Some(script),
                  fee = fee,
                  timestamp = System.currentTimeMillis())
      .explicitGet()

    val setScriptId = sender
      .signedBroadcast(sc1SetTx.json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(setScriptId)

    val swapBC1ScriptInfo = sender.addressScriptInfo(swapBC1)

    swapBC1ScriptInfo.script.isEmpty shouldBe false
    swapBC1ScriptInfo.scriptText.isEmpty shouldBe false
  }

  test("step2: make transfer to swapBC1, Bob's audit of DataTransaction with hash, txId and SC1") {
    val txToSwapBC1 =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = PrivateKeyAccount.fromSeed(sender.seed(AliceBC1)).right.get,
          recipient = PrivateKeyAccount.fromSeed(sender.seed(swapBC1)).right.get,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = 0.001.waves + 0.04.waves,
          attachment = Array.emptyByteArray
        )
        .explicitGet()

    val transferId = sender
      .signedBroadcast(txToSwapBC1.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt)))
      .id
    nodes.waitForHeightAriseAndTxPresent(transferId)

    val scriptText = ByteStr(sender.addressScriptInfo(swapBC1).script.get.getBytes())

    val hashData     = BinaryDataEntry("secret", ByteStr(secretHash.getBytes()))
    val transferData = BinaryDataEntry("transferId", ByteStr(transferId.getBytes()))
    val contractData = BinaryDataEntry("sc1", scriptText)
    val dataSC1      = List(hashData, transferData, contractData)
    val fee          = calcDataFee(dataSC1)
    val dataTxId     = sender.putData(BobBC2, dataSC1, fee).id
    nodes.waitForHeightAriseAndTxPresent(dataTxId)
  }

  test("step3: create and setup smart contract SC2") {
    val pkSwapBC2 = PrivateKeyAccount.fromSeed(sender.seed(swapBC2)).right.get
    val script    = ScriptV1(sc2).explicitGet()
    val setScriptTransaction = SetScriptTransaction
      .selfSigned(version = SetScriptTransaction.supportedVersions.head,
                  sender = pkSwapBC2,
                  script = Some(script),
                  fee = fee,
                  timestamp = System.currentTimeMillis())
      .explicitGet()

    val setScriptId = sender
      .signedBroadcast(setScriptTransaction.json() + ("type" -> JsNumber(SetScriptTransaction.typeId.toInt)))
      .id

    nodes.waitForHeightAriseAndTxPresent(setScriptId)

    val swapBC2ScriptInfo = sender.addressScriptInfo(swapBC2)

    swapBC2ScriptInfo.script.isEmpty shouldBe false
    swapBC2ScriptInfo.scriptText.isEmpty shouldBe false
  }

  test("step4: make transfer to swapBC2, Alice's audit of DataTransaction with hash, txId and SC2") {
    val txToSwapBC2 =
      TransferTransactionV2
        .selfSigned(
          version = 2,
          assetId = None,
          sender = PrivateKeyAccount.fromSeed(sender.seed(BobBC2)).right.get,
          recipient = PrivateKeyAccount.fromSeed(sender.seed(swapBC2)).right.get,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = 0.001.waves + 0.04.waves,
          attachment = Array.emptyByteArray
        )
        .explicitGet()

    val transferId = sender
      .signedBroadcast(txToSwapBC2.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt)))
      .id
    nodes.waitForHeightAriseAndTxPresent(transferId)

    val scriptText = ByteStr(sender.addressScriptInfo(swapBC2).script.get.getBytes())

    val transferData = BinaryDataEntry("transferId", ByteStr(transferId.getBytes()))
    val contractData = BinaryDataEntry("sc2", scriptText)
    val dataSC2      = List(transferData, contractData)
    val fee          = calcDataFee(dataSC2)
    val dataTxId     = sender.putData(AliceBC1, dataSC2, fee).id
    nodes.waitForHeightAriseAndTxPresent(dataTxId)
  }

  test("step5: Alice make transfer from swapBC2 to AliceBC2 ") {
    val unsigned =
      TransferTransactionV2
        .create(
          version = 2,
          assetId = None,
          sender = PrivateKeyAccount.fromSeed(sender.seed(swapBC2)).right.get,
          recipient = PrivateKeyAccount.fromSeed(sender.seed(AliceBC2)).right.get,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = fee + 0.004.waves,
          attachment = Array.emptyByteArray,
          proofs = Proofs.empty
        )
        .explicitGet()

    val sigAlice = ByteStr(crypto.sign(PrivateKeyAccount.fromSeed(sender.seed(AliceBC1)).right.get, unsigned.bodyBytes()))
    val secret   = ByteStr(secretText.getBytes())
    val signed   = unsigned.copy(proofs = Proofs(Seq(secret, sigAlice)))
    val versionedTransferId =
      sender.signedBroadcast(signed.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt))).id

    nodes.waitForHeightAriseAndTxPresent(versionedTransferId)

  }

  test("step6: Bob make transfer from swapBC1 to BobBC1 ") {
    val unsigned =
      TransferTransactionV2
        .create(
          version = 2,
          assetId = None,
          sender = PrivateKeyAccount.fromSeed(sender.seed(swapBC1)).right.get,
          recipient = PrivateKeyAccount.fromSeed(sender.seed(BobBC1)).right.get,
          amount = transferAmount,
          timestamp = System.currentTimeMillis(),
          feeAssetId = None,
          feeAmount = fee + 0.004.waves,
          attachment = Array.emptyByteArray,
          proofs = Proofs.empty
        )
        .explicitGet()

    val proof  = ByteStr(secretText.getBytes())
    val signed = unsigned.copy(proofs = Proofs(Seq(proof)))
    val versionedTransferId =
      sender.signedBroadcast(signed.json() + ("type" -> JsNumber(TransferTransactionV2.typeId.toInt))).id

    nodes.waitForHeightAriseAndTxPresent(versionedTransferId)
  }

  private def calcDataFee(data: List[DataEntry[_]]): Long = {
    val dataSize = data.map(_.toBytes.length).sum + 128
    if (dataSize > 1024) {
      fee * (dataSize / 1024 + 1)
    } else fee
  }
}
