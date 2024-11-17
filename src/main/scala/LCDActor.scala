import akka.actor.Actor
import com.phidget22._
import scala.util.Try

class LCDActor extends Actor {
  def receive: Receive = {
    case DisplayMessage(message) =>
      Try {
          val lcd = new LCD()
          lcd.open(5000)
          lcd.clear()
          lcd.writeText(LCDFont.DIMENSIONS_5X8, 0, 0, message)
          lcd.flush()
      } recover {
        case e: PhidgetException => println("LCD Error: " + e.getMessage)
      }
  }
}