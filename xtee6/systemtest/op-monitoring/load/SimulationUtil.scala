// The MIT License
// Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

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
