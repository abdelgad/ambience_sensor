import akka.actor.{ActorSystem, Props}
import com.phidget22.{HumiditySensor, LCD, LightSensor, TemperatureSensor, VoltageRatioInput}


object AmbienceSensor extends App {

  val system = ActorSystem("AmbienceSensor")

  private val lcd = new LCD()
  private val forceSensor = new VoltageRatioInput()
  private val temperatureSensor = new TemperatureSensor()
  private val humiditySensor = new HumiditySensor()
  private val lightSensor = new LightSensor()

  // Create Actors
  val lcdActor = system.actorOf(Props(new LCDActor(lcd)), "lcdActor")
  val tempSensorActor = system.actorOf(Props(new TemperatureSensorActor(temperatureSensor)), "tempSensor")
  val humiditySensorActor = system.actorOf(Props(new HumiditySensorActor(humiditySensor)), "humiditySensor")
  val lightSensorActor = system.actorOf(Props(new LightSensorActor(lightSensor)), "lightSensor")
  val webcam = system.actorOf(Props(new WebcamActor), "webcam")

  val ambienceDiffuserPath = "akka://AmbienceDiffuser@127.0.0.1:25521/user/fileClient"
  val fileServerActor = system.actorOf(Props(new FileServerActor("snapshots", ambienceDiffuserPath)), "fileServer")

  val snapshotManager = system.actorOf(Props(new SnapshotManager(lcdActor, tempSensorActor, humiditySensorActor, lightSensorActor, webcam, fileServerActor)), "snapshotManager")

  val forceSensorActor = system.actorOf(Props(new ForceSensorActor(forceSensor, snapshotManager)), "forceSensor")



  // Start the system
  snapshotManager ! Start
}
