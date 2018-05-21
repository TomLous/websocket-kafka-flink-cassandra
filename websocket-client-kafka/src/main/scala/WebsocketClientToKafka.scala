import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.TextMessage.Strict
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.Promise

/**
  * Websocket client that connects to the Websocket server and passes all messages to a kafka topic
  * Created by Tom Lous on 19/05/2018.
  */

object WebsocketClientToKafka extends App{

  // Some general config to get the actor system up and running
  val className = this.getClass.getName.split("\\$").last
  implicit val actorSystem = ActorSystem(className)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit lazy val config: Config = ConfigFactory.load()
  implicit val logger = Logger(className)

  // Define the Kafka producer
  val producerSettings = ProducerSettings(actorSystem, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(config.getString("kafka.hostPort"))

    Producer.plainSink(producerSettings)

  // Create sink that takes messages and creates producer records on the kafka topic
  val kafkaSink: Sink[Message, NotUsed] = Flow[Message].map{
    case message:Strict =>
      logger.debug(s"ws->kafka: ${message.getStrictText}")
      Some(message.getStrictText)
    case s =>
      logger.debug(s"Skip message: $s")
      None
  }
    .filter(_.isDefined)
    .map(message => new ProducerRecord[Array[Byte], String](config.getString("kafka.topic"), message.get))
    .to(Producer.plainSink(producerSettings))

  // Materialize the sink as a flow
  val flow: Flow[Message, Message, Promise[Option[Message]]] =
    Flow.fromSinkAndSourceMat(kafkaSink, Source.maybe[Message])(Keep.right)

  // Build the websocket server request
  val websocketServerUrlRequest:String = "ws://" + config.getString("websocketServer.host") + ":" + config.getString("websocketServer.port") + "/"

  // Do the websocket server request
  val (upgradeResponse, promise) =
    Http().singleWebSocketRequest(
      WebSocketRequest(websocketServerUrlRequest),
      flow)

}
