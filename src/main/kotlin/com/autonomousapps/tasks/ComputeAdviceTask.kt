package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.internal.utils.*
import com.autonomousapps.model.*
import com.autonomousapps.model.intermediates.Location
import com.autonomousapps.visitor.GraphViewReader
import com.autonomousapps.visitor.GraphViewVisitor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class ComputeAdviceTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Computes actual dependency usage"
  }

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val graph: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val locations: RegularFileProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputDirectory
  abstract val dependencies: DirectoryProperty

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val syntheticProject: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(ComputeDependencyUsageWorkAction::class.java) {
      graph.set(this@ComputeAdviceTask.graph)
      locations.set(this@ComputeAdviceTask.locations)
      dependencies.set(this@ComputeAdviceTask.dependencies)
      syntheticProject.set(this@ComputeAdviceTask.syntheticProject)
      output.set(this@ComputeAdviceTask.output)
    }
  }
}

interface ComputeDependencyUsageParameters : WorkParameters {
  val graph: RegularFileProperty
  val locations: RegularFileProperty
  val dependencies: DirectoryProperty
  val syntheticProject: RegularFileProperty
  val output: RegularFileProperty
}

abstract class ComputeDependencyUsageWorkAction : WorkAction<ComputeDependencyUsageParameters> {

  private val dependenciesDir = parameters.dependencies.get()
  private val graph = parameters.graph.fromJson<DependencyGraphView>()
  private val locations = parameters.locations.fromJsonSet<Location>()
  private val project = parameters.syntheticProject.fromJson<ProjectVariant>()

  private val dependencies = project.classpath.asSequence()
    .map(::getDependency)
    .toSet()

  override fun execute() {
    val output = parameters.output.getAndDelete()

    val reader = GraphViewReader(
      project = project,
      dependencies = dependencies,
      graph = graph,
      locations = locations
    )
    val visitor = GraphVisitor(locations)
    reader.accept(visitor)

    output.writeText(visitor.getReport().toJson())
  }

  private fun getDependency(coordinates: Coordinates): Dependency {
    return dependenciesDir.file(coordinates.toFileName()).fromJson()
  }
}

internal data class DependencyUsageReport(
  val abiDependencies: Set<Coordinates>,
  val implDependencies: Set<Coordinates>,
  val compileOnlyDependencies: Set<Coordinates>,
  val unusedDependencies: Set<Coordinates>
)

private class GraphVisitor(
  private val locations: Set<Location>
) : GraphViewVisitor {

  fun getReport() = DependencyUsageReport(
    abiDependencies = abiDependencies.mapToOrderedSet { it.coordinates },
    implDependencies = implDependencies.mapToOrderedSet { it.coordinates },
    compileOnlyDependencies = compileOnlyDependencies.mapToOrderedSet { it.coordinates },
    unusedDependencies = unusedDependencies.mapToOrderedSet { it.coordinates }
  )

  private val abiDependencies = mutableSetOf<Dependency>()
  private val implDependencies = mutableSetOf<Dependency>()
  private val compileOnlyDependencies = mutableSetOf<Dependency>()
  private val unusedDependencies = mutableSetOf<Dependency>()

  // TODO resByRes usages should probably be considered ABI?
  override fun visit(dependency: Dependency, context: GraphViewVisitor.Context) {
    var isUnusedCandidate = false
    var isLintJar = false
    var isCompileOnly = false
    var isAndroid = false
    var isRuntimeAndroid = false
    var usesResBySource = false
    var usesResByRes = false
    var usesConstant = false
    var usesInlineMember = false
    var hasServiceLoader = false
    var hasSecurityProvider = false
    var hasNativeLib = false

    fun isRuntime() = isLintJar || isRuntimeAndroid || usesResBySource || usesResByRes || usesConstant
      || usesInlineMember || hasServiceLoader || hasSecurityProvider || hasNativeLib

    dependency.capabilities.values.forEach { capability ->
      @Suppress("UNUSED_VARIABLE") // exhaustive when
      val ignored: Any = when (capability) {
        is AndroidLinterCapability -> {
          isLintJar = capability.isLintJar
        }
        is AndroidManifestCapability -> {
          val components = capability.componentMap
          val services = components[AndroidManifestCapability.Component.SERVICE]
          val providers = components[AndroidManifestCapability.Component.PROVIDER]
          val activities = components[AndroidManifestCapability.Component.ACTIVITY]
          val receivers = components[AndroidManifestCapability.Component.RECEIVER]
          // If we considered any component to be sufficient, then we'd be super over-aggressive regarding whether an
          // Android library was used.
          isRuntimeAndroid = services != null || providers != null
          // Nevertheless, it is interesting to track whether a dependency is an Android library for other reasons.
          isAndroid = isRuntimeAndroid || activities != null || receivers != null
        }
        is AndroidResCapability -> {
          // by source
          val projectImports = context.project.imports
          usesResBySource = listOf(capability.rImport, capability.rImport.removeSuffix("R") + "*").any {
            projectImports.contains(it)
          }

          // by res
          usesResByRes = capability.lines.any { (type, id) ->
            context.project.androidResSource.any { candidate ->
              val byStyleParentRef = candidate.styleParentRefs.any { styleParentRef ->
                id == styleParentRef.styleParent
              }
              val byAttrRef by lazy {
                candidate.attrRefs.any { attrRef ->
                  type == attrRef.type && id == attrRef.id
                }
              }

              byStyleParentRef || byAttrRef
            }
          }
        }
        is AnnotationProcessorCapability -> {
          // TODO haven't re-implemented annotation processing yet
        }
        is ClassCapability -> {
          if (isAbi(capability, context)) {
            abiDependencies.add(dependency)
          } else if (isImplementation(capability, context)) {
            implDependencies.add(dependency)
          } else if (isImported(capability, context)) {
            implDependencies.add(dependency)
          } else {
            isUnusedCandidate = true
          }
        }
        is ConstantCapability -> {
          val ktFiles = capability.ktFiles
          val candidateImports = capability.constants.asSequence()
            .flatMap { (fqcn, names) ->
              val ktPrefix = ktFiles.find {
                it.fqcn == fqcn
              }?.name?.let { name ->
                fqcn.removeSuffix(name)
              }
              val ktImports = names.mapNotNull { name -> ktPrefix?.let { "$it$name" } }

              ktImports + listOf("$fqcn.*") + names.map { name -> "$fqcn.$name" }
            }
            .toSet()

          usesConstant = context.project.imports.any {
            candidateImports.contains(it)
          }
        }
        is InferredCapability -> {
          isCompileOnly = capability.isCompileOnlyAnnotations
        }
        is InlineMemberCapability -> {
          val candidateImports = capability.inlineMembers.asSequence()
            .flatMap { (pn, names) ->
              listOf("$pn.*") + names.map { name -> "$pn.$name" }
            }
            .toSet()
          usesInlineMember = context.project.imports.any {
            candidateImports.contains(it)
          }
        }
        is ServiceLoaderCapability -> {
          hasServiceLoader = capability.providerClasses.isNotEmpty()
        }
        is NativeLibCapability -> {
          hasNativeLib = capability.fileNames.isNotEmpty()
        }
        is SecurityProviderCapability -> {
          hasSecurityProvider = capability.securityProviders.isNotEmpty()
        }
      }
    }

    if (isCompileOnly && !isUnusedCandidate) {
      compileOnlyDependencies.add(dependency)
      abiDependencies.remove(dependency)
      implDependencies.remove(dependency)
    }

    if (isUnusedCandidate) {
      if (isRuntime()) {
        // Not safe to declare unused as it has (undetectable) runtime elements
        implDependencies.add(dependency)
      } else {
        if (locations.any { it.identifier == dependency.coordinates.identifier }) {
          unusedDependencies.add(dependency)
        }
      }
    }
  }

  private fun isAbi(classCapability: ClassCapability, context: GraphViewVisitor.Context): Boolean {
    return context.project.exposedClasses.any { exposedClass ->
      classCapability.classes.contains(exposedClass)
    }
  }

  private fun isImplementation(classCapability: ClassCapability, context: GraphViewVisitor.Context): Boolean {
    return context.project.implementationClasses.any { implClass ->
      classCapability.classes.contains(implClass)
    }
  }

  private fun isImported(classCapability: ClassCapability, context: GraphViewVisitor.Context): Boolean {
    return context.project.imports.any { import ->
      classCapability.classes.contains(import)
    }
  }
}
