import net.jssc.{SerialPort, SerialPortException}

object ArduinoInterface {
  /**
   * Fonction bloquante pour récupérer les données de l'Arduino
   * @param portName Le port série (par ex. "COM5" ou "/dev/ttyUSB0")
   * @param duration Durée d'acquisition en secondes
   * @return (BPM, Liste des données sonores)
   */
  def collectData(portName: String, duration: Int): (Int, List[Int]) = {
    val serialPort = new SerialPort(portName)
    try {
      // Ouverture du port série
      serialPort.openPort()
      serialPort.setParams(SerialPort.BAUDRATE_9600,
        SerialPort.DATABITS_8,
        SerialPort.STOPBITS_1,
        SerialPort.PARITY_NONE)

      // Liste pour stocker les données sonores
      var soundData: List[Int] = List()
      var bpm: Option[Int] = None
      val startTime = System.currentTimeMillis()

      // Envoi d'une commande de démarrage implicite
      serialPort.writeString("E")

      while (System.currentTimeMillis() - startTime < duration * 1000) {
        // Lecture des données depuis l'Arduino
        val rawData = serialPort.readString()
        if (rawData != null) {
          rawData.trim.split("\n").foreach { line =>
            if (line.startsWith("BPM:")) {
              bpm = Some(line.stripPrefix("BPM:").trim.toInt)
            } else if (line.startsWith("SOUND:")) {
              val soundValue = line.stripPrefix("SOUND:").trim.toInt
              soundData = soundData :+ soundValue
            }
          }
        }
      }

      // Arrêt de l'acquisition
      serialPort.writeString("T")

      // Récupération finale des données
      Thread.sleep(100) // Petit délai pour s'assurer de récupérer les derniers envois
      val finalData = serialPort.readString()
      if (finalData != null) {
        finalData.trim.split("\n").foreach { line =>
          if (line.startsWith("BPM:")) {
            bpm = Some(line.stripPrefix("BPM:").trim.toInt)
          } else if (line.startsWith("SOUND:")) {
            val soundValue = line.stripPrefix("SOUND:").trim.toInt
            soundData = soundData :+ soundValue
          }
        }
      }

      (bpm.getOrElse(0), soundData)
    } catch {
      case ex: SerialPortException =>
        println(s"Erreur de communication avec le port série : ${ex.getMessage}")
        (0, List())
    } finally {
      // Fermeture du port série
      if (serialPort.isOpened) serialPort.closePort()
    }
  }
}
