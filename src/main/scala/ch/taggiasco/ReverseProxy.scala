package ch.taggiasco

import akka.actor.{ActorSystem, ActorRef}
import akka.util.ByteString
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives._
import scala.io.StdIn
import scala.concurrent.Future

 
object ReverseProxy extends Config {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("reverse-proxy")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    
    
    val log: LoggingAdapter = Logging(system, getClass)
    
    val monitoringActor: ActorRef = system.actorOf(MonitoringActor.props)
    
    val reactToTopLevelFailures = Flow[Http.IncomingConnection].watchTermination()((_, termination) => termination.onFailure {
      case cause => monitoringActor ! cause
    })
    
    val reactToConnectionFailure = Flow[HttpRequest].recover[HttpRequest] {
      case ex =>
        // handle the failure somehow
        monitoringActor ! ex
        throw ex
    }
    
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] = Http().bind(interface = httpInterface, port = httpPort)
    
    
    val domain = "127.0.0.1"
    
    val connectionFlow = Http().outgoingConnection(domain, 80, log = log)
    
    val pipeFlow = Flow[HttpRequest].via(reactToConnectionFailure).mapAsync(1)( _ match {
      case HttpRequest(method, path, headers, entity, protocol) => {
        val resp = Source.single(HttpRequest(method, path, headers, entity)).via(connectionFlow).runWith(Sink.head)
        resp
      }
      case _ =>
        Future.successful(HttpResponse(404, entity = "Unknown request"))
    })
    
    
    val binding: Future[Http.ServerBinding] = serverSource.via(reactToTopLevelFailures).to(
      Sink.foreach { connection =>
        println("Accepted new connection from " + connection.remoteAddress)
        connection handleWith pipeFlow
      }
    ).run()
    
    println(s"Server online at http://$httpInterface:$httpPort\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    
    binding.onFailure{
      case ex: Exception => log.error(ex, "Failed to bind to {}:{}!", httpInterface, httpPort)
    }
    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  
}
