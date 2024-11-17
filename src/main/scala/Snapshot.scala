import spray.json._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Case class for Snapshot
//case class Snapshot(datetime: String, humidity: Double, temperature: Double, illuminance: Double)
case class Snapshot(datetime: String, humidity: Double, temperature: Double, illuminance: Double, colors: List[String])

// JSON format for Snapshot using spray-json
object SnapshotJsonProtocol extends DefaultJsonProtocol {
  implicit val snapshotFormat: RootJsonFormat[Snapshot] = jsonFormat5(Snapshot)
}
