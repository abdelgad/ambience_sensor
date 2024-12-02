import akka.actor.Actor
import java.io.File
import java.nio.file.{Files, Paths}

case object NotifySynchronization
case class FileList(files: Set[String])
case class RequestFile(fileName: String)
case class FileContent(fileName: String, content: String)


class FileServerActor(folderPath: String, clientAddress: String) extends Actor{
  override def receive: Receive = {
    case NotifySynchronization =>
      println("Notifying client to synchronize...")
      val files = getFileList
      context.actorSelection(clientAddress) ! FileList(files)


    case RequestFile(fileName) =>
      println(s"Received request to send file: $fileName")
      readFile(fileName).foreach { content =>
        sender() ! FileContent(fileName, content)
      }
  }


  private def getFileList: Set[String] = {
    new File(folderPath)
      .listFiles()
      .filter(_.isFile)
      .map(_.getName)
      .toSet
  }


  private def readFile(fileName: String): Option[String] = {
    val filePath = Paths.get(folderPath, fileName)
    if (Files.exists(filePath)) Some(new String(Files.readAllBytes(filePath)))
    else None
  }
}