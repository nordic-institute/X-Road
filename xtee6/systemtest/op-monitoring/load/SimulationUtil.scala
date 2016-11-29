package opmonitor.loadtesting;

import java.nio.charset.StandardCharsets

import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption


// Utility functions for the load tests of operational monitoring.
object SimulationUtil {

  final val GeneratedMessageIdFile = "generated_message_ids"

  final val RandomGenerator = new util.Random

  final val Megabyte = 1024 * 1204

  def securityServerUrl(): String =
    "http://" + System.getProperty("client_security_server_url")

  def outputDirectory(): String = System.getProperty("results")

  def generateAndStoreRandomMessageId(): String = {
    val messageId = RandomGenerator.alphanumeric.take(20).mkString

    // Write the generated ID to the specified output file for later
    // analysis. Assuming the file has been created by the caller.
    Files.write(
      Paths.get(outputDirectory(), GeneratedMessageIdFile),
      (messageId + "\n").getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.APPEND)

    return messageId
  }

  def generateRandom1MbContents(): String = {
    RandomGenerator.alphanumeric.take(Megabyte).mkString
  }

}
