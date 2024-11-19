import jssc.{SerialPort, SerialPortException}
import scala.io.StdIn

object HeartRateMonitor {
  def main(args: Array[String]): Unit = {
    val portName = "COM5"
    val serialPort = new SerialPort(portName)

    try {
      // Configuration du port série
      serialPort.openPort()
      serialPort.setParams(
        SerialPort.BAUDRATE_9600,
        SerialPort.DATABITS_8,
        SerialPort.STOPBITS_1,
        SerialPort.PARITY_NONE
      )

      println("Commandes disponibles :")
      println("E - Démarrer l'enregistrement")
      println("T - Terminer l'enregistrement et afficher le BPM")
      println("Q - Quitter")

      var running = true
      while (running) {
        val input = StdIn.readLine().trim.toUpperCase

        input match {
          case "E" =>
            serialPort.writeByte('E'.toByte)
            println("Enregistrement démarré...")

          case "T" =>
            serialPort.writeByte('T'.toByte)
            Thread.sleep(1000) // Attendre la réponse de l'Arduino
            val response = serialPort.readString()
            println(s"Réponse de l'Arduino : $response")

          case "Q" =>
            println("Fin du programme")
            running = false

          case _ =>
            println("Commande invalide. Utilisez E, T, ou Q.")
        }
      }

      serialPort.closePort()
    } catch {
      case ex: SerialPortException => println(s"Erreur : ${ex.getMessage}")
    }
  }
}
