package ch.taggiasco.http.proxy

import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._


trait BaseConfig {
  private val config        = ConfigFactory.load()
  private val reverseConfig = config.getConfig("reverse-proxy")
  private val httpConfig    = reverseConfig.getConfig("http")
  
  val httpInterface = httpConfig.getString("interface")
  val httpPort      = httpConfig.getInt("port")
  val httpErrorPage = httpConfig.getString("errorpage")
  
  private val headersConfig = reverseConfig.getConfig("headers")
  
  val headers = headersConfig.entrySet().asScala.map(entry => {
    val key = entry.getKey()
    key -> headersConfig.getString(key)
  }).toMap
  
  val locations = reverseConfig.getConfigList("locations").asScala.toList
  
}
