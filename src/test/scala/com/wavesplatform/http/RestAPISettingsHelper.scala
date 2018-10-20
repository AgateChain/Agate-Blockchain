package com.wavesplatform.http

import com.typesafe.config.ConfigFactory
import com.wavesplatform.crypto
import com.wavesplatform.settings.RestAPISettings
import com.wavesplatform.utils.Base58

trait RestAPISettingsHelper {
  def apiKey: String = "test_api_key"

  lazy val MaxTransactionsPerRequest = 10000
  lazy val MaxAddressesPerRequest    = 10000

  lazy val restAPISettings = {
    val keyHash = Base58.encode(crypto.secureHash(apiKey.getBytes()))
    RestAPISettings.fromConfig(
      ConfigFactory
<<<<<<< HEAD
        .parseString(s"Agate.rest-api.api-key-hash = $keyHash")
=======
        .parseString(
          s"""
             |waves.rest-api {
             |  api-key-hash = $keyHash
             |  transactions-by-address-limit = $MaxTransactionsPerRequest
             |  distribution-by-address-limit = $MaxAddressesPerRequest
             |}
           """.stripMargin
        )
>>>>>>> 501f3836ad1f1aadb0f0a7ee82c490cb3425da1f
        .withFallback(ConfigFactory.load()))
  }
}
