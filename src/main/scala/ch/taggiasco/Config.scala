package ch.taggiasco

import com.typesafe.config.ConfigFactory


trait Config {
  private val config = ConfigFactory.load()
  private val httpConfig = config.getConfig("reverse-proxy.http")
  
  val httpInterface = httpConfig.getString("interface")
  val httpPort = httpConfig.getInt("port")
}
