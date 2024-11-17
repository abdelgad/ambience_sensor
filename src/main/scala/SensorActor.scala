import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.util.{Try, Failure, Success}
import akka.actor.{Actor, ActorRef}

import com.phidget22._

import org.bytedeco.javacv.{Frame, OpenCVFrameGrabber, OpenCVFrameConverter, Java2DFrameConverter}
import org.bytedeco.opencv.global.opencv_imgproc._
import org.bytedeco.opencv.global.opencv_imgcodecs._
import java.awt.image.BufferedImage
import java.awt.Color
import smile.clustering.kmeans


case object CaptureImage


class ForceSensorActor(snapshotManager: ActorRef) extends Actor {
  private val ACTIVATION_THRESHOLD = 0.5

  private val forceSensor = new VoltageRatioInput()
  forceSensor.setIsHubPortDevice(true)
  forceSensor.setHubPort(3)
  forceSensor.setSensorType(VoltageRatioSensorType.PN_1106)

  override def preStart(): Unit = {
    // context.system.scheduler.scheduleOnce(8.seconds, snapshotManager, ButtonPressed)

    // Set up listener for sensor value changes
    forceSensor.addSensorChangeListener((event: VoltageRatioInputSensorChangeEvent) => {
      val sensorValue = event.getSensorValue
      if (sensorValue > ACTIVATION_THRESHOLD) {
        // Button press
        snapshotManager ! ButtonPressed
      }
    })
    forceSensor.open(5000)
  }

  override def postStop(): Unit = {
    forceSensor.close()
  }

  def receive: Receive = Actor.emptyBehavior // No additional messages are handled in this actor
}


class TemperatureSensorActor extends Actor {
  def receive: Receive = {
    case ReadTemperature =>
      println("TemperatureSensorActor : receive")
      val temperature = Try {
        val sensor = new TemperatureSensor()
        sensor.open(5000)
        val value = sensor.getTemperature
        sensor.close()
        value
      }
      sender() ! temperature.toOption.map(SensorReading)
  }
}


class HumiditySensorActor extends Actor {
  def receive: Receive = {
    case ReadHumidity =>
      println("HumiditySensorActor : receive")
      val humidity = Try {
        val sensor = new HumiditySensor()
        sensor.open(5000)
        val value = sensor.getHumidity
        sensor.close()
        value
      }
      sender() ! humidity.toOption.map(SensorReading)
  }
}


class LightSensorActor extends Actor {
  def receive: Receive = {
    case ReadIlluminance =>
      println("LightSensorActor : receive")
      val illuminance = Try {
        val sensor = new LightSensor()
        sensor.open(5000)
        val value = sensor.getIlluminance
        sensor.close()
        value
      }
      sender() ! illuminance.toOption.map(SensorReading)
  }
}


class WebcamActor extends Actor {
  def receive: Receive = {
    case CaptureImage =>
      val senderRef = sender()

      val grabber = new OpenCVFrameGrabber(0) // Use the default webcam
      val result = Try {
        grabber.start()
        val frame = grabber.grab()
        // Save the frame as an image file
        saveFrameAsImage(frame, "selfie.png")

        // Extract colors from the frame
        extractMostCommonColors(frame)
      }

      result match {
        case Success(colors) =>
          senderRef ! Some(colors)
        case Failure(exception) =>
          println(s"Failed to capture image: ${exception.getMessage}")
          senderRef ! Some(Nil)
      }

      // Stop and release the webcam resource
      Try(grabber.stop()).recover {
        case ex: Exception => println(s"Error stopping webcam: ${ex.getMessage}")
      }
  }


  // Helper function to save a Frame as an image file
  private def saveFrameAsImage(frame: org.bytedeco.javacv.Frame, filename: String): Unit = {
    val converter = new OpenCVFrameConverter.ToMat()
    val mat = converter.convert(frame)

    if (mat != null && !mat.empty()) {
      imwrite(filename, mat) // Write the Mat to a file
      println(s"Frame saved as $filename")
    } else {
      println("Failed to save frame: Frame is null or empty.")
    }
  }


  // Extract the most common colors from a frame
  private def extractMostCommonColors(frame: Frame, numColors: Int = 5): List[String] = {
    // Convert Frame to BufferedImage
    val converter = new Java2DFrameConverter()
    val bufferedImage: BufferedImage = converter.getBufferedImage(frame)

    // Extract pixel colors
    val width = bufferedImage.getWidth
    val height = bufferedImage.getHeight
    val pixels = mutable.ArrayBuffer[Array[Double]]()

    for (x <- 0 until width; y <- 0 until height) {
      val color = new Color(bufferedImage.getRGB(x, y))
      pixels.append(Array(color.getRed.toDouble, color.getGreen.toDouble, color.getBlue.toDouble))
    }

    // Run K-means clustering with default Euclidean distance
    val kMeansResult = kmeans(pixels.toArray, numColors, maxIter = 100)

    // Extract cluster centers (dominant colors)
    val dominantColors = kMeansResult.centroids.map { case Array(r, g, b) =>
      new Color(r.toInt, g.toInt, b.toInt)
    }

    // Convert colors to hex strings
    dominantColors.map(colorToHex).toList
  }


  // Helper function to convert Color object to a String (Hexcode)
  private def colorToHex(color: Color): String = {
    f"#${color.getRed}%02x${color.getGreen}%02x${color.getBlue}%02x".toUpperCase
  }
}




