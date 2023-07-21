package io.github.pjfanning.licensecheck

import picocli.CommandLine

object Main extends App {
  val exitCode = new CommandLine(new LicenseCheck()).execute(args: _*)
  System.exit(exitCode)
}
