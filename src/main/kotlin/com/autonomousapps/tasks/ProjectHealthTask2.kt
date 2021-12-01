@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.advice.ComprehensiveAdvice
import com.autonomousapps.exception.BuildHealthException
import com.autonomousapps.internal.ConsoleReport
import com.autonomousapps.internal.ProjectMetrics
import com.autonomousapps.internal.advice.AdvicePrinter
import com.autonomousapps.internal.getMetricsText
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.shouldFail
import com.autonomousapps.shouldNotBeSilent
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*

abstract class ProjectHealthTask2 : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Prints advice for this project"
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val dependencyUsageReport: RegularFileProperty

  @get:Input
  abstract val dependencyRenamingMap: MapProperty<String, String>

  @TaskAction fun action() {



    // val inputFile = comprehensiveAdvice.get().asFile
    //
    // val consoleReport = ConsoleReport.from(compAdvice)
    // val advicePrinter = AdvicePrinter(consoleReport, dependencyRenamingMap.orNull)
    // val shouldFail = compAdvice.shouldFail || shouldFail()
    // val consoleText = advicePrinter.consoleText()
    //
    // // Only print to console if we're not configured to fail
    // if (!shouldFail && consoleReport.isNotEmpty()) {
    //   logger.quiet(consoleText)
    //   if (shouldNotBeSilent()) {
    //     logger.quiet(metricsText)
    //     logger.quiet("See machine-readable report at ${inputFile.path}")
    //   }
    // }
    //
    // if (shouldFail) {
    //   throw BuildHealthException(consoleText)
    // }
  }
}
