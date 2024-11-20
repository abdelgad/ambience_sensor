import akka.actor.{ActorSystem, Props}


object AmbienceSensor extends App {

  val system = ActorSystem("AmbienceSensor")

  // Create Actors
  val lcdActor = system.actorOf(Props(new LCDActor), "lcdActor")
  val tempSensor = system.actorOf(Props(new TemperatureSensorActor), "tempSensor")
  val humiditySensor = system.actorOf(Props(new HumiditySensorActor), "humiditySensor")
  val lightSensor = system.actorOf(Props(new LightSensorActor), "lightSensor")
  val webcam = system.actorOf(Props(new WebcamActor), "webcam")
  val ambienceDiffuserPath = "akka://AmbienceDiffuser@127.0.0.1:25521/user/fileClient"
  val fileServer = system.actorOf(Props(new FileServerActor("snapshots", ambienceDiffuserPath)), "fileServer")
  val snapshotManager = system.actorOf(Props(new SnapshotManager(lcdActor, tempSensor, humiditySensor, lightSensor, webcam, fileServer)), "snapshotManager")
  val forceSensor = system.actorOf(Props(new ForceSensorActor(snapshotManager)), "forceSensor")
  
  
  // Start the system
  snapshotManager ! Start
}
