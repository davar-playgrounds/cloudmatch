package io.ticofab.cm2019.node

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.pattern.pipe
import akka.stream.scaladsl.{Keep, Sink, Source, StreamRefs}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import io.ticofab.cm2019.common.Location
import io.ticofab.cm2019.common.Messages.{CheckMatchingWith, Message, MessageForMatchedDevice, YouMatchedWith}
import io.ticofab.cm2019.node.NodeManager.{FlowSource, GetFlowSource}
import wvlet.log.LogSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Device(myLocation: Location) extends Actor with LogSupport {

  implicit val as: ActorSystem = context.system
  implicit val am: ActorMaterializer = ActorMaterializer()

  info(s"phone actor ${self.path.name} created for location $myLocation")

  val (down: ActorRef, futureFlowSource: Future[FlowSource]) = {
    val (down, publisher) = Source
      .actorRef[String](1000, OverflowStrategy.fail)
      .toMat(Sink.asPublisher(fanout = false))(Keep.both)
      .run()
    val futureFlowSource = Source.fromPublisher(publisher).runWith(StreamRefs.sourceRef()).map(FlowSource)
    (down, futureFlowSource)
  }

  var matchedDevice: Option[ActorRef] = None

  override def receive: Receive = {
    case GetFlowSource => futureFlowSource
      .pipeTo(sender)
      .andThen { case _ => down ! s"Connected and handled by ${self.path.name}" }

    case CheckMatchingWith(device, itsLocation) =>
      debug(s"${self.path.name}, I got asked if I match ${device.path.name}")
      if (device != self && myLocation.isCloseEnoughTo(itsLocation)) {
        matchedDevice = Some(device)
        logMatched(self, device)
        down ! tellMatched(device)
        device ! YouMatchedWith(self)
      }

    case YouMatchedWith(device) =>
      down ! tellMatched(device)
      logMatched(self, device)

    case MessageForMatchedDevice(msg) =>
      debug(s"received a message for my matched one: $msg")
      matchedDevice.foreach(_ ! msg)

    case msg: Message =>
      debug(s"received a message for my own device: $msg")
      down ! msg.content
  }

  // logs that this phone matched with another phone
  def logMatched(me: ActorRef, it: ActorRef): Unit = info(s"phone ${me.path.name}, matched with phone ${it.path.name}")
  def tellMatched(matchedWith: ActorRef): String = s"I matched with device '${matchedWith.path.name}'!"

}
