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


trait Route {
  
  val path:   String
  val server: String
  val port:   Int
  val secure: Boolean
  val prefix: String
  
  val system: ActorSystem
  val materializer: ActorMaterializer
  
  
  private val connectionFlow = Http()(system).outgoingConnection(server, port)
  
  
  def forward(
    originMethod:   HttpMethod,
    originUri:      Uri,
    originHeaders:  Seq[HttpHeader],
    originEntity:   RequestEntity,
    originProtocol: HttpProtocol
  ): Future[HttpResponse] = {
    val uri = originUri.copy(path = Uri.Path(prefix + originUri.path))
    Source
      .single(HttpRequest(originMethod, uri, originHeaders, originEntity))
      .via(connectionFlow)
      .runWith(Sink.head)(materializer)
      .map(resp => resp)(system.dispatcher)
  }
  
}