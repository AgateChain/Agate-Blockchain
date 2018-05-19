package com.wavesplatform.settings

import com.typesafe.config.ConfigException.WrongType
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}

class FeesSettingsSpecification extends FlatSpec with Matchers {
  "FeesSettings" should "read values" in {
    val config = ConfigFactory.parseString("""Agate {
        |  network.file = "xxx"
        |  fees {
        |    payment.Agate = 100000
        |    issue.Agate = 100000000
        |    transfer.Agate = 100000
        |    reissue.Agate = 100000
        |    burn.Agate = 100000
        |    exchange.Agate = 100000
        |  }
        |  miner.timeout = 10
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2) should be(List(FeeSettings("Agate", 100000)))
    settings.fees(3) should be(List(FeeSettings("Agate", 100000000)))
    settings.fees(4) should be(List(FeeSettings("Agate", 100000)))
    settings.fees(5) should be(List(FeeSettings("Agate", 100000)))
    settings.fees(6) should be(List(FeeSettings("Agate", 100000)))
    settings.fees(7) should be(List(FeeSettings("Agate", 100000)))
  }

  it should "combine read few fees for one transaction type" in {
    val config = ConfigFactory.parseString("""Agate.fees {
        |  payment {
        |    Agate0 = 0
        |  }
        |  issue {
        |    Agate1 = 111
        |    Agate2 = 222
        |    Agate3 = 333
        |  }
        |  transfer {
        |    Agate4 = 444
        |  }
        |}
      """.stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(3)
    settings.fees(2).toSet should equal(Set(FeeSettings("Agate0", 0)))
    settings.fees(3).toSet should equal(Set(FeeSettings("Agate1", 111), FeeSettings("Agate2", 222), FeeSettings("Agate3", 333)))
    settings.fees(4).toSet should equal(Set(FeeSettings("Agate4", 444)))
  }

  it should "allow empty list" in {
    val config = ConfigFactory.parseString("Agate.fees {}".stripMargin).resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(0)
  }

  it should "override values" in {
    val config = ConfigFactory
      .parseString("""Agate.fees {
        |  payment.Agate1 = 1111
        |  reissue.Agate5 = 0
        |}
      """.stripMargin)
      .withFallback(
        ConfigFactory.parseString("""Agate.fees {
          |  payment.Agate = 100000
          |  issue.Agate = 100000000
          |  transfer.Agate = 100000
          |  reissue.Agate = 100000
          |  burn.Agate = 100000
          |  exchange.Agate = 100000
          |}
        """.stripMargin)
      )
      .resolve()

    val settings = FeesSettings.fromConfig(config)
    settings.fees.size should be(6)
    settings.fees(2).toSet should equal(Set(FeeSettings("Agate", 100000), FeeSettings("Agate1", 1111)))
    settings.fees(5).toSet should equal(Set(FeeSettings("Agate", 100000), FeeSettings("Agate5", 0)))
  }

  it should "fail on incorrect long values" in {
    val config = ConfigFactory.parseString("""Agate.fees {
        |  payment.Agate=N/A
        |}""".stripMargin).resolve()
    intercept[WrongType] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "not fail on long values as strings" in {
    val config   = ConfigFactory.parseString("""Agate.fees {
        |  transfer.Agate="1000"
        |}""".stripMargin).resolve()
    val settings = FeesSettings.fromConfig(config)
    settings.fees(4).toSet should equal(Set(FeeSettings("Agate", 1000)))
  }

  it should "fail on unknown transaction type" in {
    val config = ConfigFactory.parseString("""Agate.fees {
        |  shmayment.Agate=100
        |}""".stripMargin).resolve()
    intercept[NoSuchElementException] {
      FeesSettings.fromConfig(config)
    }
  }

  it should "override values from default config" in {
    val defaultConfig = ConfigFactory.load()
    val config        = ConfigFactory.parseString("""
        |Agate.fees {
        |  issue {
        |    Agate = 200000000
        |  }
        |  transfer {
        |    Agate = 300000
        |    "6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL" = 1
        |  }
        |  reissue {
        |    Agate = 400000
        |  }
        |  burn {
        |    Agate = 500000
        |  }
        |  exchange {
        |    Agate = 600000
        |  }
        |  lease {
        |    Agate = 700000
        |  }
        |  lease-cancel {
        |    Agate = 800000
        |  }
        |  create-alias {
        |    Agate = 900000
        |  }
        |  mass-transfer {
        |    Agate = 10000
        |  }
        |  data {
        |    Agate = 200000
        |  }
        |  set-script {
        |    Agate = 300000
        |  }
        |  sponsor-fee {
        |    Agate = 400000
        |  }
        |}
      """.stripMargin).withFallback(defaultConfig).resolve()
    val settings      = FeesSettings.fromConfig(config)
    settings.fees.size should be(12)
    settings.fees(3).toSet should equal(Set(FeeSettings("Agate", 200000000)))
    settings.fees(4).toSet should equal(Set(FeeSettings("Agate", 300000), FeeSettings("6MPKrD5B7GrfbciHECg1MwdvRUhRETApgNZspreBJ8JL", 1)))
    settings.fees(5).toSet should equal(Set(FeeSettings("Agate", 400000)))
    settings.fees(6).toSet should equal(Set(FeeSettings("Agate", 500000)))
    settings.fees(7).toSet should equal(Set(FeeSettings("Agate", 600000)))
    settings.fees(8).toSet should equal(Set(FeeSettings("Agate", 700000)))
    settings.fees(9).toSet should equal(Set(FeeSettings("Agate", 800000)))
    settings.fees(10).toSet should equal(Set(FeeSettings("Agate", 900000)))
    settings.fees(11).toSet should equal(Set(FeeSettings("Agate", 10000)))
    settings.fees(12).toSet should equal(Set(FeeSettings("Agate", 200000)))
    settings.fees(13).toSet should equal(Set(FeeSettings("Agate", 300000)))
    settings.fees(14).toSet should equal(Set(FeeSettings("Agate", 400000)))
  }
}
