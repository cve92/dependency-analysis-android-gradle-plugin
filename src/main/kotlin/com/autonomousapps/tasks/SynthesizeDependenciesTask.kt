package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.*
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
  abstract val androidLinters: RegularFileProperty

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
      androidLinters.set(this@SynthesizeDependenciesTask.androidLinters)
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
  val androidLinters: RegularFileProperty
  val manifestComponents: RegularFileProperty
  val androidRes: RegularFileProperty
  val nativeLibs: RegularFileProperty

  val outputDir: DirectoryProperty
}

abstract class SynthesizeDependenciesWorkAction : WorkAction<SynthesizeDependenciesParameters> {

  private val logger = getLogger<SynthesizeDependenciesTask>()

  override fun execute() {
    val outputDir = parameters.outputDir.getAndClean(logger)

    val physicalArtifacts = parameters.physicalArtifacts.fromJsonSet<PhysicalArtifact>()
    val explodedJars = parameters.explodedJars.fromJsonSet<ExplodedJar>()
    val inlineMembers = parameters.inlineMembers.fromJsonSet<InlineMemberDependency>()
    val serviceLoaders = parameters.serviceLoaders.fromJsonSet<ServiceLoaderDependency>()
    val annotationProcessors = parameters.annotationProcessors.fromJsonSet<AnnotationProcessorDependency>()
    // Android-specific and therefore optional
    // TODO don't need androidLinters, since it's already baked into explodedJars
    val androidLinters = parameters.androidLinters.fromNullableJsonSet<AndroidLinterDependency>().orEmpty()
    val manifestComponents = parameters.manifestComponents.fromNullableJsonSet<AndroidManifestDependency>().orEmpty()
    val androidRes = parameters.androidRes.fromNullableJsonSet<Res>().orEmpty()
    val nativeLibs = parameters.nativeLibs.fromNullableJsonSet<NativeLibDependency>().orEmpty()

    // TODO looking at all this, it looks like the "intermediates" dependencies are redundant. Can jump right to Capability
    val builders = sortedMapOf<Coordinates, DependencyBuilder>()

    fun <T : HasCoordinates> merge(
      dependencies: Set<T>,
      capabilities: (T) -> MutableList<Capability>
    ) {
      dependencies.forEach {
        builders.merge(
          it.coordinates,
          DependencyBuilder(it.coordinates, capabilities(it)),
          DependencyBuilder::concat
        )
      }
    }

    physicalArtifacts.forEach { artifact ->
      builders.merge(
        artifact.coordinates,
        DependencyBuilder(coordinates = artifact.coordinates, file = artifact.file),
        DependencyBuilder::concat
      )
    }
    merge(explodedJars) { explodedJar ->
      val capabilities = mutableListOf(
        ClassCapability(explodedJar.classes),
        ConstantCapability(explodedJar.constantFields),
        InferredCapability(isCompileOnlyAnnotations = explodedJar.isCompileOnlyAnnotations),
        KtFileCapability(explodedJar.ktFiles),
        SecurityProviderCapability(explodedJar.securityProviders)
      )
      explodedJar.androidLintRegistry?.let { capabilities += AndroidLinterCapability(it, explodedJar.isLintJar) }
      capabilities
    }
    merge(inlineMembers) { inlineMember ->
      mutableListOf(InlineMemberCapability(inlineMember.inlineMembers))
    }
    merge(serviceLoaders) { serviceLoader ->
      mutableListOf(ServiceLoaderCapability(serviceLoader.providerFile, serviceLoader.providerClasses))
    }
    merge(annotationProcessors) { proc ->
      mutableListOf(AnnotationProcessorCapability(proc.processor, proc.supportedAnnotationTypes))
    }
    merge(manifestComponents) { manifest ->
      mutableListOf(AndroidManifestCapability(manifest.packageName, manifest.componentMap))
    }
    merge(androidRes) { res ->
      mutableListOf(AndroidResCapability(res.import, res.lines))
    }
    merge(nativeLibs) { nativeLib ->
      mutableListOf(NativeLibCapability(nativeLib.fileNames))
    }

    // Write every dependency to its own file in the output directory
    builders.values.asSequence()
      .map { it.toDependency() }
      .forEach { dependency ->
        outputDir.file(dependency.coordinates.toFileName()).asFile.writeText(dependency.toJson())
      }
  }
}

private class DependencyBuilder(
  val coordinates: Coordinates,
  val capabilities: MutableList<Capability> = mutableListOf(),
  var file: File? = null
) {

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

private fun Coordinates.toFileName(): String = toString().removePrefix(":").replace(':', '-')
