
import java.nio.file.Paths
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.TextMessage.Strict
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.settings.ServerSettings
import akka.io.Inet
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}


/**
  * Websocket server reads an (OLD) EURDOL rate file from FOREX and generates ticks every n milliseconds with a generated fake timestamp small way in the past
  *
  * Created by Tom Lous on 18/05/2018.
  *
  */
object WebsocketServer extends App {

  // Some general config to get the actor system up and running
  val className = this.getClass.getName.split("\\$").last
  implicit val actorSystem = ActorSystem(className)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  implicit lazy val config: Config = ConfigFactory.load()
  implicit val logger = Logger(className)




  // Config for the input/output
  val inputDateTimeFormat = new java.text.SimpleDateFormat("yyyyMMddhhmmss")
  val outputDateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss")

  case class TickerItem(epochMs: Long, ticker: String, originalDateTime: String, open: Double, high: Double, low: Double, close: Double, volume: Int)
  implicit val tickerItemFormat = jsonFormat8(TickerItem)


  // Generate a file source to read records from (CSV file)
  val file = Paths.get("websocket-server/src/main/resources/EURUSD.txt")
  val fileSource =
    FileIO.fromPath(file)
      .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))

  // map the filesource via a delayed source and convert the csv into JSON
  val delayedSource: Source[Strict, Future[IOResult]] =
    fileSource
      .map { line =>
        Thread.sleep(config.getInt("websocketServer.sleepIntervalMs"))
        logger.debug(line.utf8String)
        val lineArray = line.utf8String.split(",").map(_.trim)
        val generatedTime = new Date().toInstant.toEpochMilli - config.getLong("websocketServer.epochDelay") - (math.random() * config.getInt("websocketServer.sleepIntervalMs") / 2).toLong

        val item = TickerItem(
          generatedTime,
          lineArray(0),
          outputDateTimeFormat.format(inputDateTimeFormat.parse(lineArray(1) + lineArray(2))),
          lineArray(3).toDouble,
          lineArray(4).toDouble,
          lineArray(5).toDouble,
          lineArray(6).toDouble,
          lineArray(7).toInt
        )
        TextMessage(item.toJson.toString())
      }

  // define a websocket route for clients to connect to and start receiving the stream of ticker items
  def route = path("") {
    extractUpgradeToWebSocket { upgrade =>
      complete({
        logger.info("Received request for data")
        upgrade.handleMessagesWithSinkSource(Sink.ignore, delayedSource)
      })
    }
  }

  // Define some webserver settings to finetune the websocket server
  val defaultSettings = ServerSettings(actorSystem)
  val pingCounter = new AtomicInteger()

  val customServerSettings = defaultSettings
    .withWebsocketSettings(
      defaultSettings
        .websocketSettings
        .withPeriodicKeepAliveData(() => ByteString(s"debug-${pingCounter.incrementAndGet()}"))
    )
    .withSocketOptions(
      List(Inet.SO.SendBufferSize(config.getInt("websocketServer.sendBufferSize")))
    )
    .withTimeouts(
      defaultSettings
        .timeouts
        .withIdleTimeout(config.getInt("websocketServer.idleTimeoutS").seconds)
        .withLingerTimeout(config.getInt("websocketServer.lingerTimeoutS").seconds)
    )


  // Bind the route to the webserver
  val bindingFuture = Http().bindAndHandle(route, "localhost", config.getInt("websocketServer.port"), settings = customServerSettings)

  bindingFuture.onComplete {
    case Success(_) =>
      logger.info(s"Server is listening on ws://localhost:${config.getInt("websocketServer.port")}")
    case Failure(e) =>
      logger.error(s"Binding failed with ${e.getMessage}")
      actorSystem.terminate()
  }
}

