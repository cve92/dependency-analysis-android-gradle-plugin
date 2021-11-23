package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.fromNullableJsonSet
import com.autonomousapps.internal.utils.toJson
import com.autonomousapps.model.*
import com.autonomousapps.services.InMemoryCache
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class SynthesizeDependenciesTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Re-synthesize dependencies from analysis"
  }

  @get:Internal
  abstract val inMemoryCache: Property<InMemoryCache>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val physicalArtifacts: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val explodedJars: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val inlineMembers: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val serviceLoaders: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val annotationProcessors: RegularFileProperty

  /*
   * Android-specific and therefore optional.
   */

  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val manifestComponents: RegularFileProperty

  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val androidRes: RegularFileProperty

  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val nativeLibs: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(SynthesizeDependenciesWorkAction::class.java) {
      inMemoryCache.set(this@SynthesizeDependenciesTask.inMemoryCache)
      physicalArtifacts.set(this@SynthesizeDependenciesTask.physicalArtifacts)
      explodedJars.set(this@SynthesizeDependenciesTask.explodedJars)
      inlineMembers.set(this@SynthesizeDependenciesTask.inlineMembers)
      serviceLoaders.set(this@SynthesizeDependenciesTask.serviceLoaders)
      annotationProcessors.set(this@SynthesizeDependenciesTask.annotationProcessors)
      manifestComponents.set(this@SynthesizeDependenciesTask.manifestComponents)
      androidRes.set(this@SynthesizeDependenciesTask.androidRes)
      nativeLibs.set(this@SynthesizeDependenciesTask.nativeLibs)
      outputDir.set(this@SynthesizeDependenciesTask.outputDir)
    }
  }
}

interface SynthesizeDependenciesParameters : WorkParameters {
  val inMemoryCache: Property<InMemoryCache>
  val physicalArtifacts: RegularFileProperty
  val explodedJars: RegularFileProperty
  val inlineMembers: RegularFileProperty
  val serviceLoaders: RegularFileProperty
  val annotationProcessors: RegularFileProperty

  // Android-specific and therefore optional
  val manifestComponents: RegularFileProperty
  val androidRes: RegularFileProperty
  val nativeLibs: RegularFileProperty

  val outputDir: DirectoryProperty
}

abstract class SynthesizeDependenciesWorkAction : WorkAction<SynthesizeDependenciesParameters> {

  private val builders = sortedMapOf<Coordinates, DependencyBuilder>()

  override fun execute() {
    val outputDir = parameters.outputDir

    val physicalArtifacts = parameters.physicalArtifacts.fromJsonSet<PhysicalArtifact>()
    val explodedJars = parameters.explodedJars.fromJsonSet<ExplodedJar>()
    val inlineMembers = parameters.inlineMembers.fromJsonSet<InlineMemberDependency>()
    val serviceLoaders = parameters.serviceLoaders.fromJsonSet<ServiceLoaderDependency>()
    val annotationProcessors = parameters.annotationProcessors.fromJsonSet<AnnotationProcessorDependency>()
    // Android-specific and therefore optional
    val manifestComponents = parameters.manifestComponents.fromNullableJsonSet<AndroidManifestDependency>().orEmpty()
    val androidRes = parameters.androidRes.fromNullableJsonSet<Res>().orEmpty()
    val nativeLibs = parameters.nativeLibs.fromNullableJsonSet<NativeLibDependency>().orEmpty()


    physicalArtifacts.forEach { artifact ->
      builders.merge(
        artifact.coordinates,
        DependencyBuilder(artifact.coordinates).apply { file = artifact.file },
        DependencyBuilder::concat
      )
    }
    merge(explodedJars) { it.toCapabilities() }
    merge(inlineMembers) { listOf(it.toCapability()) }
    merge(serviceLoaders) { listOf(it.toCapability()) }
    merge(annotationProcessors) { listOf(it.toCapability()) }
    merge(manifestComponents) { listOf(AndroidManifestCapability(it.packageName, it.componentMap)) }
    merge(androidRes) { listOf(it.toCapability()) }
    merge(nativeLibs) { listOf(it.toCapability()) }

    // Write every dependency to its own file in the output directory
    builders.values.asSequence()
      .map { it.toDependency() }
      .forEach { dependency ->
        outputDir.file(dependency.coordinates.toFileName()).get().asFile.writeText(dependency.toJson())
      }
  }

  private fun <T : HasCoordinates> merge(
    dependencies: Set<T>,
    newCapabilities: (T) -> List<Capability>
  ) {
    dependencies.forEach {
      builders.merge(
        it.coordinates,
        DependencyBuilder(it.coordinates).apply { capabilities.addAll(newCapabilities(it)) },
        DependencyBuilder::concat
      )
    }
  }
}

private class DependencyBuilder(val coordinates: Coordinates) {

  val capabilities: MutableList<Capability> = mutableListOf()
  var file: File? = null

  fun concat(other: DependencyBuilder): DependencyBuilder {
    other.file?.let { file = it }
    capabilities.addAll(other.capabilities)
    return this
  }

  fun toDependency(): Dependency {
    val file = checkNotNull(file) { "'file' must not be null" }
    val capabilities = capabilities.sorted()

    return when (coordinates) {
      is ProjectCoordinates -> ProjectDependency(coordinates, capabilities, file)
      is ModuleCoordinates -> ModuleDependency(coordinates, capabilities, file)
      is FlatCoordinates -> FlatDependency(coordinates, capabilities, file)
    }
  }
}

// TODO I'll need this reading files too
private fun Coordinates.toFileName() = toString().replace(":", "__") + ".json"
