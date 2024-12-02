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


case object Start
case object ButtonPressed


class SnapshotManager(
                       lcdActor: ActorRef,
                       tempSensor: ActorRef,
                       humiditySensor: ActorRef,
                       lightSensor: ActorRef,
                       webcamActor: ActorRef,
                       fileServer: ActorRef
                     ) extends Actor {

  private implicit val timeout: Timeout = Timeout(20.seconds) // Timeout for ask pattern
  private var isRecording = false // Flag to track recording state


  def receive: Receive = {
    case Start =>
      lcdActor ! DisplayMessage("Press button to record...")
      println("System ready. Press button to record...")
      context.system.scheduler.scheduleWithFixedDelay(0.seconds, 5.seconds, fileServer, NotifySynchronization)

    case ButtonPressed =>
      if (!isRecording) {
        lcdActor ! DisplayMessage("Recording...Hold still")
        println("Recording started")
        isRecording = true
        collectSensorDataAndSaveSnapshot()
      }

    case WifiReconnected =>
      fileServer ! NotifySynchronization
      
    case _ =>
      println("Unknown message received.")
  }


  private def collectSensorDataAndSaveSnapshot(): Unit = {
    // Request data from sensors using the ask pattern
    val temperatureFuture = (tempSensor ? ReadTemperature).mapTo[Option[SensorReading]]
    val humidityFuture = (humiditySensor ? ReadHumidity).mapTo[Option[SensorReading]]
    val illuminanceFuture = (lightSensor ? ReadIlluminance).mapTo[Option[SensorReading]]
    val colorsFuture = (webcamActor ? CaptureImage).mapTo[Option[List[String]]]


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
      colors <- colorsOpt
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
        isRecording = false // Reset recording state after saving snapshot
      case Success(None) =>
        lcdActor ! DisplayMessage("Failed to collect data !")
        context.system.scheduler.scheduleOnce(3.seconds, lcdActor, DisplayMessage("Press button to record..."))
        println("Failed to collect all sensor data.")
        isRecording = false // Reset recording state after saving snapshot
      case Failure(ex) =>
        lcdActor ! DisplayMessage("Failed to collect data !")
        context.system.scheduler.scheduleOnce(3.seconds, lcdActor, DisplayMessage("Press button to record..."))
        println(s"Error during data collection: ${ex.getMessage}")
        isRecording = false // Reset recording state after saving snapshot
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
    lcdActor ! DisplayMessage("Snapshot saved !")
    context.system.scheduler.scheduleOnce(3.seconds, lcdActor, DisplayMessage("Press button to record..."))
    fileServer ! NotifySynchronization
  }
}
