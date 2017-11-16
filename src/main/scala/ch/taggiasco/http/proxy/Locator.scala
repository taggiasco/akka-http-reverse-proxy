package ch.taggiasco.http.proxy

import com.typesafe.config.{Config, ConfigException}
import scala.collection.JavaConverters._
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import scala.util.Try


sealed case class Locator private(
  val path:          String,
  val server:        String,
  val port:          Int,
  val secure:        Boolean,
  val loosySSL:      Boolean,
  val prefix:        String,
  val headers:       Map[String, String],
  val commonHeaders: Map[String, String],
  val testOnStart:   Boolean
)(implicit val system: ActorSystem, val materializer: ActorMaterializer, val logger: LoggingAdapter) extends Route {
  logger.info(s"Location is built for path = $path, server = $server, port = $port, secure = $secure, loosySSL = $loosySSL, prefix = $prefix")
}


object Locator {
  
  private def loadLocator(
    config:        com.typesafe.config.Config,
    commonHeaders: Map[String, String]
  )(implicit system: ActorSystem, materializer: ActorMaterializer, logger: LoggingAdapter): Locator = {
    val headersConfig = config.getConfig("headers")
    val headers = headersConfig.entrySet().asScala.map(entry => {
      val key = entry.getKey()
      key -> headersConfig.getString(key)
    }).toMap
    
    val secure = config.getBoolean("secure")
    val loosySSL = {
      if(secure) {
        Try( config.getBoolean("loosySSL") ).toOption.getOrElse(false)
      } else {
        false
      }
    }
    Locator(
      config.getString("path"),
      config.getString("server"),
      config.getInt("port"),
      secure,
      loosySSL,
      config.getString("prefix"),
      headers,
      commonHeaders,
      config.getBoolean("test")
    )
  }
  
  
  def apply(config: BaseConfig)(implicit system: ActorSystem, materializer: ActorMaterializer, logger: LoggingAdapter): List[Locator] = {
    try {
      config.locations.map(loadLocator(_, config.headers))
    } catch {
      case e: ConfigException =>
        throw new Exception("Error on configuration loading : " + e.getMessage())
    }
  }
  
}
