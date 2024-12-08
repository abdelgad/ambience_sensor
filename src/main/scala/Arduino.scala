import jssc.{SerialPort, SerialPortException}
import akka.actor.{Actor, ActorLogging}

import scala.util.Try

case class ArduinoReading(data: (Double, Double)) {
  def bpm: Double = data._1
  def dbs: Double = data._2
}
case object ReadArduino

class Arduino extends Actor with ActorLogging {
  def receive: Receive = {
    case ReadArduino =>
      val bpmAnddbs = Try {
        collectData("COM3", 5)
      }
      sender() ! bpmAnddbs.toOption.map { case (bpm, dbs) => ArduinoReading((bpm, dbs)) }

    case other =>
      log.warning(s"Unhandled message: $other")
  }

  def collectData(portName: String, duration: Int): (Int, Int) = {
    val serialPort = new SerialPort(portName)
    try {
      serialPort.openPort()
      serialPort.setParams(
        SerialPort.BAUDRATE_9600,
        SerialPort.DATABITS_8,
        SerialPort.STOPBITS_1,
        SerialPort.PARITY_NONE
      )
      var soundData: List[Int] = List()
      var bpm: Option[Int] = None
      val startTime = System.currentTimeMillis()

      serialPort.writeString("E")
      while (System.currentTimeMillis() - startTime < duration * 1000) {
        Option(serialPort.readString()).foreach { rawData =>
          rawData.trim.split("\n").foreach { line =>
            if (line.startsWith("BPM: ")) bpm = Some(line.stripPrefix("BPM: ").trim.toInt)
            else if (line.startsWith("DBS: "))
              line.stripPrefix("DBS: ").split(", ").map(_.trim.toIntOption).foreach(_.foreach(soundData :+= _))
          }
        }
      }
      serialPort.writeString("T")
      (bpm.getOrElse(0), averageL(soundData))
    } catch {
      case ex: SerialPortException =>
        log.error(s"Serial port error: ${ex.getMessage}")
        (0, 0)
    } finally {
      if (serialPort.isOpened) serialPort.closePort()
    }
  }

  def averageL(lst: List[Int]): Int = if (lst.nonEmpty) lst.sum / lst.size else 0
}
