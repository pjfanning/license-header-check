package io.github.pjfanning.licensecheck

import picocli.CommandLine.{Command, Parameters}

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import scala.collection.mutable
import scala.io.Source

@Command(
  name = "license-check",
  mixinStandardHelpOptions = true,
  version = Array("license-check 0.1"),
  description = Array(
    "Checks the provided directory and outputs license header summary."
  )
)
class LicenseCheck extends Callable[Unit] {
  val aslText = "Licensed to the Apache Software Foundation (ASF)"

  @Parameters(
    index = "0",
    description = Array("The directory to recursively scan")
  )
  private var dir: File = null

  override def call(): Unit = {
    if (!dir.isDirectory) {
      throw new RuntimeException(s"Not a directory: ${dir.getAbsolutePath}")
    }
    val map = mutable.Map[String, Seq[String]]()
    scanDir(s"${dir.getAbsolutePath}${File.pathSeparator}", dir, map)
    println("Results:")
    map.foreach { case (key, fileNames) =>
      println()
      println(key)
      println("---------------------------")
      fileNames.foreach(println)
    }
  }

  private def scanDir(basePath: String, inputDir: File, map: mutable.Map[String, Seq[String]]): Unit = {
    inputDir.listFiles().foreach { file =>
      if (file.isDirectory) {
        scanDir(basePath, file, map)
      } else if (shouldCheckFile(file)) {
        findCopyrightLines(file).foreach { matches =>
          val key = matches.mkString(System.lineSeparator())
          val path = removePrefix(basePath, file.getAbsolutePath)
          map.get(key) match {
            case Some(fileNames) => map.put(key, fileNames :+ path)
            case _ => map.put(key, Seq(path))
          }
        }
      }
    }
  }

  private def shouldCheckFile(file: File): Boolean = {
    val name = file.getName
    name.endsWith(".scala") || name.endsWith(".java") || name.endsWith(".sbt")
  }

  private def findCopyrightLines(file: File): Option[Seq[String]] = {
    val lineIter = Source.fromFile(file, StandardCharsets.UTF_8.name()).getLines()
    val buffer = mutable.Buffer[String]()
    var apacheLicensed = false
    while(lineIter.hasNext) {
      val line = lineIter.next()
      val trimmed = ltrim(line)
      if (isCommentLine(trimmed)) {
        if (trimmed.toLowerCase.contains("copyright"))
          buffer.append(trimmed.trim)
        if (!apacheLicensed && trimmed.contains(aslText))
          apacheLicensed = true
      }
    }
    if (apacheLicensed) {
      buffer.prepend(aslText)
    }
    Option.when(buffer.nonEmpty)(buffer.toSeq)
  }

  private def ltrim(s: String) = s.replaceAll("^\\s+", "")

  private def isCommentLine(line: String): Boolean = {
    line.startsWith("*") || line.startsWith("/") || line.startsWith("#")
  }

  private def removePrefix(prefix: String, text: String): String = {
    if (text.startsWith(prefix)) {
      text.substring(prefix.length)
    } else {
      text
    }
  }
}