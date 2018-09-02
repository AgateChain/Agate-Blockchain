sealed abstract class Network(val suffix: String) {
<<<<<<< HEAD
  val name = s"Agate${if (suffix == "mainnet") "" else "-" + suffix}"
=======
  lazy val packageSuffix = if (suffix == Mainnet.suffix) "" else "-" + suffix
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9
  override val toString = suffix
}

object Network {
  def apply(v: Option[String]) = v match {
    case Some(Testnet.suffix) => Testnet
    case Some(Devnet.suffix) => Devnet
    case _ => Mainnet
  }
}

object Mainnet extends Network("mainnet")
object Testnet extends Network("testnet")
object Devnet extends Network("devnet")
