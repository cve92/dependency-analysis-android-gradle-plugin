package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.internal.utils.fromJson
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.getAndDelete
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
    description = "Provides advice on how best to declare the project's dependencies"
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
    workerExecutor.noIsolation().submit(ComputeAdviceWorkAction::class.java) {
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

abstract class ComputeAdviceWorkAction : WorkAction<ComputeAdviceParameters> {

  private val dependenciesDir = parameters.dependencies.get()
  private val graph = parameters.graph.fromJson<DependencyGraphView>()
  private val locations = parameters.graph.fromJsonSet<Location>()
  private val project = parameters.syntheticProject.fromJson<ProjectVariant>()

  // TODO maybe this could just be lazy?
  private val dependencies = project.classpath.asSequence()
    .map(::getDependency)
    .toSet()

  override fun execute() {
    val output = parameters.output.getAndDelete()

    val reader = GraphViewReader(
      project,
      dependencies,
      graph,
      locations
    )
    val visitor = GraphVisitor()
    reader.accept(visitor)


  }

  private fun getDependency(coordinates: Coordinates): Dependency {
    return dependenciesDir.file(coordinates.toFileName()).fromJson()
  }
}

private class GraphVisitor : GraphViewVisitor {

  val abiDependencies = mutableSetOf<Dependency>()
  val implDependencies = mutableSetOf<Dependency>()

  override fun visit(dependency: Dependency, context: GraphViewVisitor.Context) {
    if (isApi(dependency, context)) {
      abiDependencies.add(dependency)
    }
    if (isImplementation(dependency, context)) {
      implDependencies.add(dependency)
    }
  }

  private fun isApi(dependency: Dependency, context: GraphViewVisitor.Context): Boolean {
    return context.project.exposedClasses.any { exposedClass ->
      dependency.capabilityOf<ClassCapability>()?.classes.orEmpty().contains(exposedClass)
    }
  }

  private fun isImplementation(dependency: Dependency, context: GraphViewVisitor.Context): Boolean {
    return context.project.implementationClasses.any { implClass ->
      dependency.capabilityOf<ClassCapability>()?.classes.orEmpty().contains(implClass)
    }
  }
}
