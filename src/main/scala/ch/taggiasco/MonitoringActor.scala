package ch.taggiasco

import akka.actor._


class MonitoringActor extends Actor {
  
  def receive = {
    case cause: String =>
      println("MonitoringActor : " + cause)
    case e: Throwable =>
      println("MonitoringActor : " + e.getMessage)
    case _ =>
      println("MonitoringActor : oups...")
  }
}


object MonitoringActor {
  
  def props(): Props = Props(new MonitoringActor())
  
}
