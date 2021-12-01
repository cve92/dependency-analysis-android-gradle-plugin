package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.unsafeLazy
import com.autonomousapps.internal.utils.*
import com.autonomousapps.model.*
import com.autonomousapps.model.intermediates.DependencyUsageReport
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
    group = TASK_GROUP_DEP_INTERNAL
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
    workerExecutor.noIsolation().submit(ComputeAdviceAction::class.java) {
      graph.set(this@ComputeAdviceTask.graph)
      locations.set(this@ComputeAdviceTask.locations)
      dependencies.set(this@ComputeAdviceTask.dependencies)
      syntheticProject.set(this@ComputeAdviceTask.syntheticProject)
      output.set(this@ComputeAdviceTask.output)
    }
  }
}

interface ComputeAdviceParameters : WorkParameters {
  val graph: RegularFileProperty
  val locations: RegularFileProperty
  val dependencies: DirectoryProperty
  val syntheticProject: RegularFileProperty
  val output: RegularFileProperty
}

abstract class ComputeAdviceAction : WorkAction<ComputeAdviceParameters> {

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
    val visitor = GraphVisitor(project.variant)
    reader.accept(visitor)

    output.writeText(visitor.getReport().toJson())
  }

  private fun getDependency(coordinates: Coordinates): Dependency {
    return dependenciesDir.file(coordinates.toFileName()).fromJson()
  }
}

private class GraphVisitor(private val variant: String) : GraphViewVisitor {

  fun getReport() = DependencyUsageReport(
    variant = variant,
    abiDependencies = apiDependencies.mapToOrderedSet { it.coordinates },
    implDependencies = implDependencies.mapToOrderedSet { it.coordinates },
    compileOnlyDependencies = compileOnlyDependencies.mapToOrderedSet { it.coordinates },
    runtimeOnlyDependencies = runtimeOnlyDependencies.mapToOrderedSet { it.coordinates },
    compileOnlyApiDependencies = compileOnlyApiDependencies.mapToOrderedSet { it.coordinates },
  )

  /*
   * App projects don't have APIs. This is handled upstream by ensuring app projects don't attempt ABI analysis.
   * Test variants also don't have APIs. This is not yet handled at all. (Love comments destined to be out of date.)
   */

  private val apiDependencies = mutableSetOf<Dependency>()
  private val implDependencies = mutableSetOf<Dependency>()
  private val compileOnlyDependencies = mutableSetOf<Dependency>()
  private val runtimeOnlyDependencies = mutableSetOf<Dependency>()
  private val compileOnlyApiDependencies = mutableSetOf<Dependency>()

  override fun visit(dependency: Dependency, context: GraphViewVisitor.Context) {
    var isUnusedCandidate = false
    var isLintJar = false
    var isCompileOnly = false
    var isRuntimeAndroid = false
    var usesResBySource = false
    var usesResByRes = false
    var usesConstant = false
    var usesInlineMember = false
    var hasServiceLoader = false
    var hasSecurityProvider = false
    var hasNativeLib = false

    dependency.capabilities.values.forEach { capability ->
      @Suppress("UNUSED_VARIABLE") // exhaustive when
      val ignored: Any = when (capability) {
        is AndroidLinterCapability -> isLintJar = capability.isLintJar
        is AndroidManifestCapability -> isRuntimeAndroid = isRuntimeAndroid(capability)
        is AndroidResCapability -> {
          usesResBySource = usesResBySource(capability, context)
          usesResByRes = usesResByRes(capability, context)
        }
        is AnnotationProcessorCapability -> {
          // TODO haven't re-implemented annotation processing yet
        }
        is ClassCapability -> {
          if (isAbi(capability, context)) {
            apiDependencies.add(dependency)
          } else if (isImplementation(capability, context)) {
            implDependencies.add(dependency)
          } else if (isImported(capability, context)) {
            implDependencies.add(dependency)
          } else {
            isUnusedCandidate = true
          }
        }
        is ConstantCapability -> usesConstant = usesConstant(capability, context)
        is InferredCapability -> isCompileOnly = capability.isCompileOnlyAnnotations
        is InlineMemberCapability -> usesInlineMember = usesInlineMember(capability, context)
        is ServiceLoaderCapability -> hasServiceLoader = capability.providerClasses.isNotEmpty()
        is NativeLibCapability -> hasNativeLib = capability.fileNames.isNotEmpty()
        is SecurityProviderCapability -> hasSecurityProvider = capability.securityProviders.isNotEmpty()
      }
    }

    if (isCompileOnly && !isUnusedCandidate) {
      compileOnlyDependencies.add(dependency)
      apiDependencies.remove(dependency) // TODO compileOnlyApi?
      implDependencies.remove(dependency)
    }

    if (isUnusedCandidate) {
      // These weren't detected by direct presence in bytecode, but via source analysis. We can say less about them, so
      // we dump them into `implementation` to be conservative.
      if (usesResBySource) {
        implDependencies.add(dependency)
      } else if (usesResByRes) {
        // TODO resByRes usages should probably be considered ABI?
        implDependencies.add(dependency)
      } else if (usesConstant) {
        implDependencies.add(dependency)
      } else if (usesInlineMember) {
        implDependencies.add(dependency)
      }

      // Not safe to declare unused as it has (undetectable) runtime elements
      if (isLintJar) {
        runtimeOnlyDependencies.add(dependency)
      } else if (isRuntimeAndroid) {
        runtimeOnlyDependencies.add(dependency)
      } else if (hasServiceLoader) {
        runtimeOnlyDependencies.add(dependency)
      } else if (hasSecurityProvider) {
        runtimeOnlyDependencies.add(dependency)
      } else if (hasNativeLib) {
        runtimeOnlyDependencies.add(dependency)
      }
    }
  }

  private fun isRuntimeAndroid(capability: AndroidManifestCapability): Boolean {
    val components = capability.componentMap
    val services = components[AndroidManifestCapability.Component.SERVICE]
    val providers = components[AndroidManifestCapability.Component.PROVIDER]
    // If we considered any component to be sufficient, then we'd be super over-aggressive regarding whether an Android
    // library was used.
    return services != null || providers != null
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

  private fun usesConstant(capability: ConstantCapability, context: GraphViewVisitor.Context): Boolean {
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

    return context.project.imports.any {
      candidateImports.contains(it)
    }
  }

  private fun usesResBySource(capability: AndroidResCapability, context: GraphViewVisitor.Context): Boolean {
    val projectImports = context.project.imports
    return listOf(capability.rImport, capability.rImport.removeSuffix("R") + "*").any {
      projectImports.contains(it)
    }
  }

  private fun usesResByRes(capability: AndroidResCapability, context: GraphViewVisitor.Context): Boolean {
    return capability.lines.any { (type, id) ->
      context.project.androidResSource.any { candidate ->
        val byStyleParentRef = candidate.styleParentRefs.any { styleParentRef ->
          id == styleParentRef.styleParent
        }
        val byAttrRef by unsafeLazy {
          candidate.attrRefs.any { attrRef ->
            type == attrRef.type && id == attrRef.id
          }
        }

        byStyleParentRef || byAttrRef
      }
    }
  }

  private fun usesInlineMember(capability: InlineMemberCapability, context: GraphViewVisitor.Context): Boolean {
    val candidateImports = capability.inlineMembers.asSequence()
      .flatMap { (pn, names) ->
        listOf("$pn.*") + names.map { name -> "$pn.$name" }
      }
      .toSet()
    return context.project.imports.any {
      candidateImports.contains(it)
    }
  }
}
