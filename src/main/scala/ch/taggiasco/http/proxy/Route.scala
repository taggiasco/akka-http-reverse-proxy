package ch.taggiasco.http.proxy

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import scala.concurrent.Future
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.Uri.Host
import akka.http.scaladsl.model.Uri.ParsingMode
import akka.event.LoggingAdapter
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.akka.AkkaSSLConfig


trait Route {
  
  val path:        String
  val server:      String
  val port:        Int
  val secure:      Boolean
  val loosySSL:    Boolean
  val prefix:      String
  val headers:     Map[String, String]
  val testOnStart: Boolean
  
  val system:       ActorSystem
  val materializer: ActorMaterializer
  val logger:       LoggingAdapter
  
  
  private val defaultCharset = java.nio.charset.Charset.defaultCharset()
  
  private val connectionFlow = {
    if(secure) {
      if(loosySSL) {
        val badSslConfig = AkkaSSLConfig()(system).mapSettings(s => s.withLoose(s.loose.withDisableSNI(true)))
        val badCtx = Http()(system).createClientHttpsContext(badSslConfig)
        Http()(system).outgoingConnectionHttps(server, port, log = logger, connectionContext = badCtx)
      } else {
        Http()(system).outgoingConnectionHttps(server, port, log = logger)
      }
    } else {
      Http()(system).outgoingConnection(server, port, log = logger)
    }
  }
  
  
  def test() {
    if(testOnStart) {
      logger.info(s"Test : starting test for locator on $path")
      Source
        .single(HttpRequest(HttpMethods.GET, Uri("/")))
        .via(connectionFlow)
        .runWith(Sink.head)(materializer)
        .map(resp => {
          logger.info(s"Result : end of test for locator on $path with status : ${resp.status}")
          resp
        })(system.dispatcher)
    }
  }
  
  
  
  private def removeFirstSlashes(s: String): String = {
    if(s.startsWith("/")) {
      removeFirstSlashes(s.drop(1))
    } else {
      s
    }
  }
  
  
  private def removeFromPath(path: Uri.Path, prefix: String): String = {
    val temp = path.toString().replaceFirst(prefix, "")
    removeFirstSlashes(temp)
  }
  
  
  private def buildURI(originUri: Uri): Uri = {
    val scheme = if(secure) { "https" } else { "http" }
    val host = Host(server, defaultCharset, ParsingMode.Strict)
    val authority = originUri.authority.copy(host, port)
    originUri.copy(scheme = scheme, path = Uri.Path(prefix + "/" + removeFromPath(originUri.path, path)), authority = authority)
  }
  
  
  def forward(
    requestId:      String,
    originMethod:   HttpMethod,
    originUri:      Uri,
    originHeaders:  Seq[HttpHeader],
    originEntity:   RequestEntity,
    originProtocol: HttpProtocol
  ): Future[HttpResponse] = {
    val uri = buildURI(originUri)
    logger.info(s"Request $requestId : URI is : $uri")
    Source
      .single(HttpRequest(originMethod, uri, originHeaders, originEntity, originProtocol))
      .via(connectionFlow)
      .runWith(Sink.head)(materializer)
      .map(resp => {
        logger.info(s"Response for request $requestId is : ${resp.status}")
        resp
      })(system.dispatcher)
  }
  
  
  def log(
    originMethod:   HttpMethod,
    originUri:      Uri,
    originHeaders:  Seq[HttpHeader],
    originEntity:   RequestEntity,
    originProtocol: HttpProtocol
  ): Future[HttpResponse] = {
    val uri = buildURI(originUri)
    val data =
      s"""
      Méthode : $originMethod
      URI : $originUri
      URI details : ${originUri.scheme}, ${originUri.authority}, ${originUri.path}, ${originUri.queryString(defaultCharset)}, ${originUri.fragment}
      Headers : $originHeaders
      Entity : $originEntity
      Protocol : $originProtocol
      
      Path : $path
      Server : $server
      Port : $port
      Sécurisé : $secure
      Préfixe : $prefix
      
      URI : $uri
      """
    Future.successful(HttpResponse(200, entity = data))
  }
  
}