package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.mapToSet
import com.autonomousapps.model.Advice
import com.autonomousapps.model.Coordinates
import com.autonomousapps.model.intermediates.DependencyUsageReport
import com.autonomousapps.model.intermediates.DependencyUsageReport.Reason
import com.autonomousapps.model.intermediates.Location
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class MergeAdviceTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Merges dependency usage reports from variant-specific computations"
  }

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val dependencyUsageReports: ListProperty<RegularFile>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val locations: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(MergeAdviceAction::class.java) {
      dependencyUsageReports.set(this@MergeAdviceTask.dependencyUsageReports)
      locations.set(this@MergeAdviceTask.locations)
      output.set(this@MergeAdviceTask.output)
    }
  }
}

interface MergeAdviceParameters : WorkParameters {
  val dependencyUsageReports: ListProperty<RegularFile>
  val locations: RegularFileProperty
  val output: RegularFileProperty
}

abstract class MergeAdviceAction : WorkAction<MergeAdviceParameters> {

  private val locations = parameters.locations.fromJsonSet<Location>()

  override fun execute() {
    val output = parameters.output.getAndDelete()
    val reports = parameters.dependencyUsageReports.get()
      .mapToSet { it.fromJson<DependencyUsageReport>() }

    val usages = UsageBuilder(reports).usages
    val advice = sortedSetOf<Advice>()

    

    // reports.forEach { report ->
    //   report.abiDependencies.forEach { abiDependency ->
    //     val current = locationsFor(abiDependency)
    //
    //     // TODO this is all wrong. Many to many, damnit!
    //     if (current.isEmpty()) {
    //       // we must add this dependency
    //       advice.add(Advice.ofAdd(abiDependency.coordinates, "api"))
    //     } else if (current.size == 1) {
    //       // this dependency is declared once. We may have to change the bucket
    //       val fromConfiguration = current.first().configurationName
    //       if (!fromConfiguration.endsWith("api")) {
    //         advice.add(Advice.ofChange(abiDependency.coordinates, fromConfiguration, "api"))
    //       }
    //     }
    //   }
    // }

    // output.writeText(advice.toJson())
  }

  // This project might declare this dependency multiple times, on different configurations
  private fun locationsFor(trace: DependencyUsageReport.Trace): Set<Location> {
    return locations.asSequence()
      .filter { it.identifier == trace.coordinates.identifier }
      // For now, we ignore any special dependencies like test fixtures or platforms
      .filter { it.attributes.isEmpty() }
      .toSet()
  }
}

private class UsageBuilder(reports: Set<DependencyUsageReport>) {

  val usages: Map<Coordinates, Set<Usage>>

  init {
    val usages = mutableMapOf<Coordinates, MutableSet<Usage>>()

    reports.forEach { report ->
      report.abiDependencies.forEach { abiDependency ->
        usages.add(report, abiDependency, Bucket.API)
      }
      report.implDependencies.forEach { implDependency ->
        usages.add(report, implDependency, Bucket.IMPL)
      }
      report.compileOnlyDependencies.forEach { compileOnlyDependency ->
        usages.add(report, compileOnlyDependency, Bucket.COMPILE_ONLY)
      }
      report.runtimeOnlyDependencies.forEach { runtimeOnlyDependency ->
        usages.add(report, runtimeOnlyDependency, Bucket.RUNTIME_ONLY)
      }
      // report.compileOnlyApiDependencies.forEach { abiDependency ->
      // }
    }

    this@UsageBuilder.usages = usages
  }

  private fun MutableMap<Coordinates, MutableSet<Usage>>.add(
    report: DependencyUsageReport,
    trace: DependencyUsageReport.Trace,
    bucket: Bucket
  ) {
    val usage = Usage(
      variant = report.variant,
      bucket = bucket,
      reasons = trace.reasons
    )
    merge(trace.coordinates, mutableSetOf(usage)) { acc, inc ->
      acc.apply { addAll(inc) }
    }
  }
}

private class Usage(
  private val variant: String,
  private val bucket: Bucket,
  private val reasons: Set<Reason>
) {

}

private enum class Bucket(val value: String) {
  API("api"),
  IMPL("implementation"),
  COMPILE_ONLY("compileOnly"),
  RUNTIME_ONLY("runtimeOnly"),
}
