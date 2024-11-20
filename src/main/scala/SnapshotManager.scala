import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.util.Success
import scala.util.Failure
import spray.json.*

import java.io.{File, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import SnapshotJsonProtocol.*


// Messages for button events and sensor readings
case object Start
case object ButtonPressed
case object ReadTemperature
case object ReadHumidity
case object ReadIlluminance
case class DisplayMessage(message: String)
case class SensorReading(value: Double)


class SnapshotManager(
                       lcdActor: ActorRef,
                       tempSensor: ActorRef,
                       humiditySensor: ActorRef,
                       lightSensor: ActorRef,
                       webcamActor: ActorRef,
                       fileServer : ActorRef
                     ) extends Actor {

  private implicit val timeout: Timeout = Timeout(5.seconds) // Timeout for ask pattern


  def receive: Receive = {
    case Start =>
      lcdActor ! DisplayMessage("Press button to record...")
      println("System ready. Press button to record...")
    //      fileServer ! NotifySynchronization

    case ButtonPressed =>
      lcdActor ! DisplayMessage("Recording started...Hold still")
      println("Recording started...Hold still")
      collectSensorDataAndSaveSnapshot()

    case _ =>
      println("Unknown message received.")
  }


  private def collectSensorDataAndSaveSnapshot(): Unit = {
    // Request data from sensors using the ask pattern
    val temperatureFuture = (tempSensor ? ReadTemperature).mapTo[Option[SensorReading]]
    val humidityFuture = (humiditySensor ? ReadHumidity).mapTo[Option[SensorReading]]
    val illuminanceFuture = (lightSensor ? ReadIlluminance).mapTo[Option[SensorReading]]
    val colorsFuture = (webcamActor ? CaptureImage).mapTo[Option[List[String]]] // Expect Option[List[String]]


    // Combine the futures
    val combinedFuture: Future[Option[Snapshot]] = for {
      tempOpt <- temperatureFuture
      humidityOpt <- humidityFuture
      illuminanceOpt <- illuminanceFuture
      colorsOpt <- colorsFuture
    } yield for {
      temp <- tempOpt
      humidity <- humidityOpt
      illuminance <- illuminanceOpt
      colors <- colorsOpt // Unwrap Option[List[String]]
    } yield {
      Snapshot(
        datetime = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        humidity = humidity.value,
        temperature = temp.value,
        illuminance = illuminance.value,
        colors = colors
      )
    }

    // Handle the combined response
    combinedFuture.onComplete {
      case Success(Some(snapshot)) =>
        saveSnapshot(snapshot)
        lcdActor ! DisplayMessage("Snapshot saved successfully.")
        println(s"Snapshot saved: $snapshot")
      case Success(None) =>
        lcdActor ! DisplayMessage("Failed to collect all sensor data.")
        println("Failed to collect all sensor data.")
      case Failure(ex) =>
        lcdActor ! DisplayMessage("Error during data collection.")
        println(s"Error during data collection: ${ex.getMessage}")
    }
  }

  private def saveSnapshot(snapshot: Snapshot): Unit = {
    // Ensure the snapshots folder exists
    val folder = new File("snapshots")
    if (!folder.exists()) folder.mkdir()

    // Sanitize the datetime string for use in the file name
    val sanitizedDatetime = snapshot.datetime.replaceAll(":", "-")

    // Save the snapshot to a file
    val filePath = s"snapshots/snapshot_$sanitizedDatetime.json"
    val writer = new PrintWriter(filePath)
    writer.write(snapshot.toJson.prettyPrint)
    writer.close()

    println(s"Snapshot saved to $filePath")
    fileServer ! NotifySynchronization
  }
}
