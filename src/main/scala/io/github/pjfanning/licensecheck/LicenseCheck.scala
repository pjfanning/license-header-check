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
  val aslText2 = "Licensed under the Apache License"

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
    scanDir(s"${dir.getAbsolutePath}${File.separator}", dir, map)
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
        val matches = findCopyrightLines(file)
        val key = if (matches.isEmpty) "No License" else matches.mkString(System.lineSeparator())
        val path = removePrefix(basePath, file.getAbsolutePath)
        map.get(key) match {
          case Some(fileNames) => map.put(key, fileNames :+ path)
          case _ => map.put(key, Seq(path))
        }
      }
    }
  }

  private def shouldCheckFile(file: File): Boolean = {
    val name = file.getName
    name.endsWith(".scala") || name.endsWith(".java") || name.endsWith(".groovy") ||
      name.endsWith(".sbt") || name.endsWith(".template")
  }

  private def findCopyrightLines(file: File): Seq[String] = {
    val lineIter = Source.fromFile(file, StandardCharsets.UTF_8.name()).getLines()
    val buffer = mutable.Buffer[String]()
    var apacheLicenseText: Option[String] = None
    while(lineIter.hasNext) {
      val line = lineIter.next()
      val trimmed = ltrim(line)
      if (isCommentLine(trimmed)) {
        if (trimmed.toLowerCase.contains("copyright"))
          buffer.append(trimmed.trim)
        if (apacheLicenseText.isEmpty) {
          if (trimmed.contains(aslText)) apacheLicenseText = Some(aslText)
          else if (trimmed.contains(aslText2)) apacheLicenseText = Some(aslText2)
        }
      }
    }
    apacheLicenseText.foreach { text =>
      buffer.prepend(text)
    }
    buffer.toSeq
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