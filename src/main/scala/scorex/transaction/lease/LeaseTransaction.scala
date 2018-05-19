package scorex.transaction.lease

import com.google.common.primitives.{Bytes, Longs}
import monix.eval.Coeval
import play.api.libs.json.{JsObject, Json}
import scorex.account.{Address, AddressOrAlias, PublicKeyAccount}
import scorex.transaction.{AssetId, ProvenTransaction, ValidationError}
import scala.util.Try
import scorex.crypto.signatures.Curve25519.KeyLength

trait LeaseTransaction extends ProvenTransaction {
  def amount: Long
  def fee: Long
  def recipient: AddressOrAlias
  def version: Byte
  override val assetFee: (Option[AssetId], Long) = (None, fee)

  override final val json: Coeval[JsObject] = Coeval.evalOnce(
    jsonBase() ++ Json.obj(
      "version"   -> version,
      "amount"    -> amount,
      "recipient" -> recipient.stringRepr,
      "fee"       -> fee,
      "timestamp" -> timestamp
    ))

  protected final val bytesBase = Coeval.evalOnce(
    Bytes.concat(sender.publicKey, recipient.bytes.arr, Longs.toByteArray(amount), Longs.toByteArray(fee), Longs.toByteArray(timestamp)))
}

object LeaseTransaction {

  object Status {
    val Active   = "active"
    val Canceled = "canceled"
  }

  def validateLeaseParams(amount: Long, fee: Long, recipient: AddressOrAlias, sender: PublicKeyAccount) =
    if (amount <= 0) {
      Left(ValidationError.NegativeAmount(amount, "Agate"))
    } else if (Try(Math.addExact(amount, fee)).isFailure) {
      Left(ValidationError.OverflowError)
    } else if (fee <= 0) {
      Left(ValidationError.InsufficientFee())
    } else if (recipient.isInstanceOf[Address] && sender.stringRepr == recipient.stringRepr) {
      Left(ValidationError.ToSelf)
    } else Right(())

  def parseBase(bytes: Array[Byte], start: Int) = {
    val sender = PublicKeyAccount(bytes.slice(start, KeyLength))
    for {
      recRes <- AddressOrAlias.fromBytes(bytes, start + KeyLength)
      (recipient, recipientEnd) = recRes
      quantityStart             = recipientEnd
      quantity                  = Longs.fromByteArray(bytes.slice(quantityStart, quantityStart + 8))
      fee                       = Longs.fromByteArray(bytes.slice(quantityStart + 8, quantityStart + 16))
      end                       = quantityStart + 24
      timestamp                 = Longs.fromByteArray(bytes.slice(quantityStart + 16, end))
    } yield (sender, recipient, quantity, fee, timestamp, end)
  }

}
