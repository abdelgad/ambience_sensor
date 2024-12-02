import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.Cancellable
import com.phidget22.{LCD, LCDFont, PhidgetException}


case class DisplayMessage(message: String)
case object TurnOffBackLight

class LCDActor(lcd: LCD) extends Actor {

  private var scheduledTask: Option[Cancellable] = None
  
  
  def receive: Receive = {
    case DisplayMessage(message) =>
      Try {
        lcd.open(5000)
        lcd.clear()

        // Set the backlight to 50%
        lcd.setBacklight(0.5)

        // Get the screen width and height
        val screenWidth = lcd.getWidth
        val screenHeight = lcd.getHeight

        // Get the font width and height (DIMENSIONS_5X8 has width=5, height=8)
        val fontWidth = 5
        val fontHeight = 8
        
        // Calculate the horizontal starting position to center the text
        val xPosition = (screenWidth - (message.length * fontWidth)) / 2
        // Calculate the vertical starting position to center the text
        val yPosition = (screenHeight - fontHeight) / 2
        
        lcd.writeText(LCDFont.DIMENSIONS_5X8, xPosition, yPosition, message)
        lcd.flush()

        // Cancel any previously scheduled task (if any)
        scheduledTask.foreach(_.cancel())
        // Schedule turning off the backlight after 5 seconds
        scheduledTask = Some(context.system.scheduler.scheduleOnce(5.seconds, self, TurnOffBackLight))
        
      } recover {
        case e: PhidgetException => println("LCD Error: " + e.getMessage)
      }

    case TurnOffBackLight =>
      Try {
        lcd.setBacklight(0)
      } recover {
        case e: PhidgetException => println("Error turning off backlight: " + e.getMessage)
      }
  }
}