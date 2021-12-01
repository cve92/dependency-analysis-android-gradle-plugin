package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.model.intermediates.DependencyUsageReport
import com.autonomousapps.model.intermediates.Location
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
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

  override fun execute() {
    val output = parameters.output.getAndDelete()
    val locations = parameters.locations.fromJsonSet<Location>()
    val reports = parameters.dependencyUsageReports.get().map {
      it.fromJson<DependencyUsageReport>()
    }

    
  }
}
