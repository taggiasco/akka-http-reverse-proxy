package ch.taggiasco

import akka.actor.ActorSystem
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
    
    
    val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] = Http().bind(interface = httpInterface, port = httpPort)
    
    
    val requestHandler: HttpRequest => HttpResponse = {
      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "<html><body>Hello world!</body></html>")
        )
      
      case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
        HttpResponse(entity = "PONG!")
      
      case HttpRequest(GET, Uri.Path("/crash"), _, _, _) =>
        sys.error("BOOM!")
      
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }
    
    
    val binding: Future[Http.ServerBinding] = serverSource.to(
      Sink.foreach { connection =>
        println("Accepted new connection from " + connection.remoteAddress)
        connection handleWithSyncHandler requestHandler
      }
    ).run()
    
    println(s"Server online at http://$httpInterface:$httpPort\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    
    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  
}
