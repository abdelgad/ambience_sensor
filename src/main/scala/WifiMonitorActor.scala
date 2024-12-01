import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._
import scala.sys.process._

case object WifiReconnected

case object WifiDisconnected

case object CheckWifiStatus

class WifiMonitorActor(snapshotManager: ActorRef) extends Actor {
  import context.dispatcher

  private var isConnected: Boolean = false

  // Periodically check Wi-Fi status
  private val scheduler = context.system.scheduler.scheduleAtFixedRate(
    initialDelay = 0.seconds,
    interval = 3.seconds,
    receiver = self,
    message = CheckWifiStatus
  )

  override def postStop(): Unit = {
    scheduler.cancel()
    super.postStop()
  }

  def receive: Receive = {
    case CheckWifiStatus =>
      val currentlyConnected = isWifiConnected
      (isConnected, currentlyConnected) match {
        case (false, true) => // Transition: Disconnected -> Connected
          self ! WifiReconnected
        case (true, false) => // Transition: Connected -> Disconnected
          self ! WifiDisconnected
        case _ => // No change
      }
      isConnected = currentlyConnected

    case WifiDisconnected =>
      println("WifiMonitorActor : WiFi disconnected")

    case WifiReconnected =>
      println("WifiMonitorActor: WiFi reconnected")
      snapshotManager ! WifiReconnected
  }

  private def isWifiConnected: Boolean = {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("win")) {
      isWindowsWifiConnected
    } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
      isLinuxWifiConnected
    } else {
      false
    }
  }

  private def isWindowsWifiConnected: Boolean = {
    val output = "netsh wlan show interfaces".!!
    output.contains("State : connected")
  }

  private def isLinuxWifiConnected: Boolean = {
    val output = Seq("bash", "-c", "nmcli -t -f TYPE,STATE dev | grep wifi").!!
    output.contains("wifi:connected")
  }
}
