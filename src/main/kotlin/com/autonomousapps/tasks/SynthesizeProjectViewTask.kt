package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.*
import com.autonomousapps.model.*
import com.autonomousapps.model.intermediates.ExplodingAbi
import com.autonomousapps.model.intermediates.ExplodingBytecode
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
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

  @get:Input
  abstract val variant: Property<String>

  /** [`DependencyGraphView`][DependencyGraphView] */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val graph: RegularFileProperty

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

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(SynthesizeProjectViewWorkAction::class.java) {
      variant.set(this@SynthesizeProjectViewTask.variant)
      graph.set(this@SynthesizeProjectViewTask.graph)
      explodedBytecode.set(this@SynthesizeProjectViewTask.explodedBytecode)
      explodedSourceCode.set(this@SynthesizeProjectViewTask.explodedSourceCode)
      explodingAbi.set(this@SynthesizeProjectViewTask.explodingAbi)
      androidResSource.set(this@SynthesizeProjectViewTask.androidResSource)
      output.set(this@SynthesizeProjectViewTask.output)
    }
  }
}

interface SynthesizeProjectViewParameters : WorkParameters {
  val variant: Property<String>
  val graph: RegularFileProperty
  val explodedBytecode: RegularFileProperty
  val explodedSourceCode: RegularFileProperty

  // Optional
  val explodingAbi: RegularFileProperty
  val androidResSource: RegularFileProperty

  val output: RegularFileProperty
}

abstract class SynthesizeProjectViewWorkAction : WorkAction<SynthesizeProjectViewParameters> {

  private val builders = sortedMapOf<String, BytecodeSourceBuilder>()

  override fun execute() {
    val output = parameters.output.getAndDelete()

    val graph = parameters.graph.fromJson<DependencyGraphView>()
    val explodedBytecode = parameters.explodedBytecode.fromJsonSet<ExplodingBytecode>()
    val explodingAbi = parameters.explodingAbi.fromNullableJsonSet<ExplodingAbi>().orEmpty()
    val explodedSourceCode = parameters.explodedSourceCode.fromJsonSet<RawCodeSource>()
    val androidResSource = parameters.androidResSource.fromNullableJsonSet<AndroidResSource>().orEmpty()

    explodedBytecode.forEach { bytecode ->
      builders.merge(
        bytecode.className,
        BytecodeSourceBuilder(bytecode.className).apply {
          relativePath = bytecode.relativePath
          usedClasses.addAll(bytecode.usedClasses)
        },
        BytecodeSourceBuilder::concat
      )
    }
    explodingAbi.forEach { abi ->
      builders.merge(
        abi.className,
        BytecodeSourceBuilder(abi.className).apply {
          exposedClasses.addAll(abi.exposedClasses)
        },
        BytecodeSourceBuilder::concat
      )
    }
    explodedSourceCode.forEach { source ->
      builders.merge(
        source.className,
        BytecodeSourceBuilder(source.className).apply {
          imports.addAll(source.imports)
        },
        BytecodeSourceBuilder::concat
      )
    }

    val bytecodeSource = builders.values.asSequence()
      .map { it.build() }
      .toSet()

    @Suppress("UnstableApiUsage") // Guava Graph
    val projectVariant = ProjectVariant(
      variant = parameters.variant.get(),
      sources = (bytecodeSource + androidResSource).toList(),
      classpath = graph.graph.nodes()
    )

    output.writeText(projectVariant.toJson())
  }
}

private class BytecodeSourceBuilder(val className: String) {

  var relativePath: String? = null
  val usedClasses = mutableSetOf<String>()
  val exposedClasses = mutableSetOf<String>()
  val imports = mutableSetOf<String>()

  fun concat(other: BytecodeSourceBuilder): BytecodeSourceBuilder {
    usedClasses.addAll(other.usedClasses)
    exposedClasses.addAll(other.exposedClasses)
    imports.addAll(other.imports)
    other.relativePath?.let { relativePath = it }
    return this
  }

  fun build(): BytecodeSource {
    val relativePath = checkNotNull(relativePath) { "'relativePath' must not be null" }
    return BytecodeSource(
      relativePath = relativePath,
      className = className,
      usedClasses = usedClasses,
      exposedClasses = exposedClasses
    )
  }
}
