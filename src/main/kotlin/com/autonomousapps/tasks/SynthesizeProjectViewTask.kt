package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.fromNullableJsonSet
import com.autonomousapps.model.AndroidResSource
import com.autonomousapps.model.RawCodeSource
import com.autonomousapps.model.intermediates.ExplodingAbi
import com.autonomousapps.model.intermediates.ExplodingBytecode
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class SynthesizeProjectViewTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Synthesizes project usages information into a single view"
  }

  /** [`Set<ExplodingByteCode>`][ExplodingBytecode] */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val explodedBytecode: RegularFileProperty

  /** [`Set<RawCodeSource>`][RawCodeSource] */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val explodedSourceCode: RegularFileProperty

  /** [`Set<ExplodingAbi>`][ExplodingAbi] */
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val explodingAbi: RegularFileProperty

  /** [`Set<AndroidResSource>`][AndroidResSource] */
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val androidResSource: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(SynthesizeProjectViewWorkAction::class.java) {
      explodedBytecode.set(this@SynthesizeProjectViewTask.explodedBytecode)
      explodedSourceCode.set(this@SynthesizeProjectViewTask.explodedSourceCode)
      explodingAbi.set(this@SynthesizeProjectViewTask.explodingAbi)
      androidResSource.set(this@SynthesizeProjectViewTask.androidResSource)
    }
  }
}

interface SynthesizeProjectViewParameters : WorkParameters {
  val explodedBytecode: RegularFileProperty
  val explodedSourceCode: RegularFileProperty
  val explodingAbi: RegularFileProperty
  val androidResSource: RegularFileProperty
}

abstract class SynthesizeProjectViewWorkAction : WorkAction<SynthesizeProjectViewParameters> {

  override fun execute() {
    val explodedBytecode = parameters.explodedBytecode.fromJsonSet<ExplodingBytecode>()
    val explodedSourceCode = parameters.explodedSourceCode.fromJsonSet<RawCodeSource>()
    val explodingAbi = parameters.explodingAbi.fromNullableJsonSet<ExplodingAbi>().orEmpty()
    val androidResSource = parameters.androidResSource.fromNullableJsonSet<AndroidResSource>().orEmpty()


  }
}
