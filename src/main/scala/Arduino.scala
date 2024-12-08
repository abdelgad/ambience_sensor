import jssc.{SerialPort, SerialPortException}
import akka.actor.{Actor, ActorRef}

case class ArduinoReading(data: (Double, Double)) {
  def bpm: Double = data._1
  def dbs: Double = data._2
}
case object ReadArduino

class Arduino extends Actor {
  def receive: Receive = {
    case ReadArduino =>
      val bpmAnddbs = Try {
        collectData("COM3", 5)
      }
      sender() ! bpmAnddbs.toOption.map{ case (bpm, dbs) => ArduinoReading((bpm, dbs)) }
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

      // Initialisation des variables
      var soundData: List[Int] = List()
      var bpm: Option[Int] = None
      val startTime = System.currentTimeMillis()

      // Commande de démarrage
      serialPort.writeString("E")
      Thread.sleep(500) // Attendre le début de l’enregistrement

      while (System.currentTimeMillis() - startTime < duration * 1000) {
        val rawData = serialPort.readString()
        if (rawData != null && rawData.nonEmpty) {
          rawData.trim.split("\n").foreach { line =>
            if (line.startsWith("BPM: ")) {
              bpm = Some(line.stripPrefix("BPM: ").trim.toInt)
            } else if (line.startsWith("DBS: ")) {
              line.stripPrefix("DBS: ").split(", ").map(_.trim.toFloat).foreach { soundValue =>
                soundData = soundData :+ soundValue.toInt
              }
            }
          }
        }
      }

      // Arrêt de l'enregistrement
      serialPort.writeString("T")
      Thread.sleep(1000) // Assurer que toutes les données sont reçues

      // Dernière lecture des données
      val finalData = serialPort.readString()
      if (finalData != null && finalData.nonEmpty) {
        finalData.trim.split("\n").foreach { line =>
          if (line.startsWith("BPM: ")) {
            bpm = Some(line.stripPrefix("BPM: ").trim.toInt)
          } else if (line.startsWith("DBS: ")) {
            line.stripPrefix("DBS: ").split(", ").map(_.trim.toFloat).foreach { soundValue =>
              soundData = soundData :+ soundValue.toInt
            }
          }
        }
      }

      (bpm.getOrElse(0), averageL(soundData))
    } catch {
      case ex: SerialPortException =>
        println(s"Erreur de communication avec le port série : ${ex.getMessage}")
        (0, 0)
    } finally {
      if (serialPort.isOpened) serialPort.closePort()
    }
  }

  def averageL(lst: List[Int]): Int = {
    lst.sum / lst.size
  }

  def main(args: Array[String]): Unit = {
    println(collectData("COM3", 5))
  }
}
