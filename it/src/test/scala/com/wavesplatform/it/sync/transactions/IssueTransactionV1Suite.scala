package com.wavesplatform.it.sync.transactions

import com.wavesplatform.it.api.SyncHttpApi._
import com.wavesplatform.it.transactions.BaseTransactionSuite
import com.wavesplatform.it.util._
import com.wavesplatform.it.sync._
import org.scalatest.prop.TableDrivenPropertyChecks

class IssueTransactionV1Suite extends BaseTransactionSuite with TableDrivenPropertyChecks {

<<<<<<< HEAD
  private val defaultQuantity = 100000
  private val assetFee        = 5.Agate

  test("asset issue changes issuer's asset balance; issuer's Agate balance is decreased by fee") {
=======
  test("asset issue changes issuer's asset balance; issuer's waves balance is decreased by fee") {
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9
    val assetName        = "myasset"
    val assetDescription = "my asset description"
    val (balance1, eff1) = notMiner.accountBalances(firstAddress)

    val issuedAssetId = sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = true, issueFee).id
    nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

    notMiner.assertBalances(firstAddress, balance1 - issueFee, eff1 - issueFee)
    notMiner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
  }

  test("Able to create asset with the same name") {
    val assetName        = "myasset1"
    val assetDescription = "my asset description 1"
    val (balance1, eff1) = notMiner.accountBalances(firstAddress)

    val issuedAssetId = sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = false, issueFee).id
    nodes.waitForHeightAriseAndTxPresent(issuedAssetId)

    val issuedAssetIdSameAsset = sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = true, issueFee).id
    nodes.waitForHeightAriseAndTxPresent(issuedAssetIdSameAsset)

    notMiner.assertAssetBalance(firstAddress, issuedAssetId, someAssetAmount)
    notMiner.assertBalances(firstAddress, balance1 - 2 * issueFee, eff1 - 2 * issueFee)
  }

  test("Not able to create asset when insufficient funds") {
    val assetName        = "myasset"
    val assetDescription = "my asset description"
    val eff1             = notMiner.accountBalances(firstAddress)._2
    val bigAssetFee      = eff1 + 1.Agate

<<<<<<< HEAD
    assertBadRequestAndMessage(sender.issue(firstAddress, assetName, assetDescription, defaultQuantity, 2, reissuable = false, bigAssetFee),
                               "negative Agate balance")
=======
    assertBadRequestAndMessage(sender.issue(firstAddress, assetName, assetDescription, someAssetAmount, 2, reissuable = false, bigAssetFee),
                               "negative waves balance")
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9
  }

  val invalidAssetValue =
    Table(
      ("assetVal", "decimals", "message"),
      (0l, 2, "negative amount"),
      (1l, 9, "Too big sequences requested"),
      (-1l, 1, "negative amount"),
      (1l, -1, "Too big sequences requested")
    )

  forAll(invalidAssetValue) { (assetVal: Long, decimals: Int, message: String) =>
    test(s"Not able to create asset total token='$assetVal', decimals='$decimals' ") {
      val assetName          = "myasset2"
      val assetDescription   = "my asset description 2"
      val decimalBytes: Byte = decimals.toByte
      assertBadRequestAndMessage(sender.issue(firstAddress, assetName, assetDescription, assetVal, decimalBytes, reissuable = false, issueFee),
                                 message)
    }
  }

}
