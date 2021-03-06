package com.wavesplatform.transaction

import org.scalacheck.Gen
import com.wavesplatform.state.{ByteStr, EitherExt2}
import play.api.libs.json._
import com.wavesplatform.account.{AddressScheme, PublicKeyAccount}
import com.wavesplatform.transaction.assets.{IssueTransactionV1, ReissueTransactionV2}

class ReissueTransactionV2Specification extends GenericTransactionSpecification[ReissueTransactionV2] {

  def transactionParser: com.wavesplatform.transaction.TransactionParserFor[ReissueTransactionV2] = ReissueTransactionV2

  def updateProofs(tx: ReissueTransactionV2, p: Proofs): ReissueTransactionV2 = {
    tx.copy(proofs = p)
  }

  def assertTxs(first: ReissueTransactionV2, second: ReissueTransactionV2): Unit = {
    first.sender.address shouldEqual second.sender.address
    first.timestamp shouldEqual second.timestamp
    first.fee shouldEqual second.fee
    first.version shouldEqual second.version
    first.quantity shouldEqual second.quantity
    first.reissuable shouldEqual second.reissuable
    first.assetId shouldEqual second.assetId
    first.proofs shouldEqual second.proofs
    first.bytes() shouldEqual second.bytes()
  }

  def generator: Gen[((Seq[com.wavesplatform.transaction.Transaction], ReissueTransactionV2))] =
    for {
      version                                                                  <- versionGen(ReissueTransactionV2)
      (sender, assetName, description, quantity, decimals, _, iFee, timestamp) <- issueParamGen
      fee                                                                      <- smallFeeGen
      reissuable                                                               <- Gen.oneOf(true, false)
    } yield {
      val issue = IssueTransactionV1.selfSigned(sender, assetName, description, quantity, decimals, reissuable = true, iFee, timestamp).explicitGet()
      val reissue1 = ReissueTransactionV2
        .selfSigned(version, AddressScheme.current.chainId, sender, issue.assetId(), quantity, reissuable = reissuable, fee, timestamp)
        .explicitGet()
      (Seq(issue), reissue1)
    }

  def jsonRepr: Seq[(JsValue, ReissueTransactionV2)] =
    Seq(
      (Json.parse("""{
                       "type": 5,
                       "id": "HbQ7gMoDyRxSU6LbLLBVNTbxASaR8rm4Zck6eYvWVUkB",
                       "sender": "3N5GRqzDBhjVXnCn44baHcz2GoZy5qLxtTh",
                       "senderPublicKey": "FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z",
                       "fee": 100000000,
                       "timestamp": 1526287561757,
                       "proofs": [
                       "4DFEtUwJ9gjMQMuEXipv2qK7rnhhWEBqzpC3ZQesW1Kh8D822t62e3cRGWNU3N21r7huWnaty95wj2tZxYSvCfro"
                       ],
                       "version": 2,
                       "chainId": 84,
                       "assetId": "9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz",
                       "quantity": 100000000,
                       "reissuable": true
                    }
    """),
       ReissueTransactionV2
         .create(
           2,
           'T',
           PublicKeyAccount.fromBase58String("FM5ojNqW7e9cZ9zhPYGkpSP1Pcd8Z3e3MNKYVS5pGJ8Z").explicitGet(),
           ByteStr.decodeBase58("9ekQuYn92natMnMq8KqeGK3Nn7cpKd3BvPEGgD6fFyyz").get,
           100000000L,
           true,
           100000000L,
           1526287561757L,
           Proofs(Seq(ByteStr.decodeBase58("4DFEtUwJ9gjMQMuEXipv2qK7rnhhWEBqzpC3ZQesW1Kh8D822t62e3cRGWNU3N21r7huWnaty95wj2tZxYSvCfro").get))
         )
         .right
         .get))

  def transactionName: String = "ReissueTransactionV2"
}
