package com.wavesplatform.settings

import com.wavesplatform.Version
import scorex.utils.ScorexLogging

/**
  * System constants here.
  */
object Constants extends ScorexLogging {
  val ApplicationName = "Agate"
  val AgentName       = s"Agate v${Version.VersionString}"

  val UnitsInWave = 100000000L
  val TotalWaves  = 100000000L
}
