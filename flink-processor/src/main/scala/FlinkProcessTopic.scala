import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011
import org.apache.flink.streaming.api.scala._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}



/**
  * Reads from kafka topic and processes it using flink in windows
  *
  * Created by Tom Lous on 21/05/2018.
  */
object FlinkProcessTopic extends App {
  // Some general config to get the actor system up and running
  val className = this.getClass.getName.split("\\$").last
  val streamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
  implicit lazy val config: Config = ConfigFactory.load()
  implicit val logger = Logger(className)


  // Set Flink properties
  val properties = new Properties()
  properties.setProperty("bootstrap.servers", config.getString("kafka.hostPort"))
  properties.setProperty("group.id", config.getString("kafka.groupId"))

  val stream = streamExecutionEnvironment
    .addSource(
      new FlinkKafkaConsumer011[String](config.getString("kafka.topic"), new SimpleStringSchema(), properties)
        .setStartFromEarliest()
    )
    // @todo continue here
    .print

  streamExecutionEnvironment.execute(config.getString("jobName"))
}
