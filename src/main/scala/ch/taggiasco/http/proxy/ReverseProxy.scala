package ch.taggiasco.http.proxy

import akka.actor.{ActorSystem, ActorRef}
import akka.util.ByteString
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import scala.io.StdIn
import scala.concurrent.Future
import scala.util.Failure
import akka.http.scaladsl.server.directives.DebuggingDirectives

 
object ReverseProxy extends BaseConfig {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("reverse-proxy")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    
    
    val locats = Locator(this)
    require(locats.nonEmpty)
    
    val locations = locats.map(loc => loc.path -> loc)
    
    
    val log: LoggingAdapter = Logging(system, getClass)
    
    
    val reactToTopLevelFailures = Flow[Http.IncomingConnection].watchTermination()((_, termination) => termination.onComplete {
      case Failure(cause) => log.error(cause, "Top level failure")
      case _ =>
    })
    
    val reactToConnectionFailure = Flow[HttpRequest].recover[HttpRequest] {
      case ex =>
        log.error(ex, "Connection failure")
        throw ex
    }
    
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] = Http().bind(interface = httpInterface, port = httpPort)
    
    
    val pipeToLocatorFlow = Flow[HttpRequest].via(reactToConnectionFailure).mapAsync(1)( _ match {
      case HttpRequest(method, path, headers, entity, protocol) => {
        locations.find(loc => path.path.startsWith(Uri.Path(loc._1))) match {
          case Some(locator) =>
            locator._2.forward(method, path, headers, entity, protocol)
            // forward
          case None =>
            // reverse non defined
            Future.successful(HttpResponse(404, entity = "Unknown path"))
        }
        
        //val resp = Source.single(HttpRequest(method, path, headers, entity)).via(connectionFlow).runWith(Sink.head)
        //resp
      }
      case _ =>
        Future.successful(HttpResponse(404, entity = "Unknown request"))
    })
    
    
    val binding: Future[Http.ServerBinding] = serverSource.via(reactToTopLevelFailures).to(
      Sink.foreach { connection =>
        println("Accepted new connection from " + connection.remoteAddress)
        connection handleWith pipeToLocatorFlow
      }
    ).run()
    
    println(s"Server online at http://$httpInterface:$httpPort\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    
    binding.onComplete{
      case Failure(ex) => log.error(ex, "Failed to bind to {}:{}!", httpInterface, httpPort)
      case _ =>
    }
    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  
}
