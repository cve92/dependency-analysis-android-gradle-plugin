package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.artifactsFor
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.mapNotNullToSet
import com.autonomousapps.internal.utils.toCoordinates
import com.autonomousapps.model.Coordinates
import com.autonomousapps.model.ProjectCoordinates
import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.support.appendReproducibleNewLine
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class GraphViewTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Constructs a variant-specific view of this project's dependency graph"
  }

  private lateinit var compileClasspath: Configuration

  fun setCompileClasspath(compileClasspath: Configuration) {
    this.compileClasspath = compileClasspath
  }

  @get:Internal
  abstract val jarAttr: Property<String>

  @Classpath
  fun getCompileClasspath(): FileCollection = compileClasspath
    .artifactsFor(jarAttr.get())
    .artifactFiles

  @get:OutputFile
  abstract val outputDot: RegularFileProperty

  @TaskAction fun action() {
    val outputDot = outputDot.getAndDelete()

    val graph = GraphViewBuilder(compileClasspath).graph

    outputDot.writeText(GraphWriter.toDot(graph))
  }
}

/**
 * Walks the resolved dependency graph to create a dependency graph rooted on the current project.
 */
@Suppress("UnstableApiUsage") // Guava Graph
private class GraphViewBuilder(conf: Configuration) {

  val graph: Graph<Coordinates>

  private val graphBuilder = GraphBuilder.directed()
    .allowsSelfLoops(false)
    .immutable<Coordinates>()

  private val visited = mutableSetOf<Coordinates>()

  init {
    val root = conf
      .incoming
      .resolutionResult
      .root

    walkFileDeps(root, conf)
    walk(root)

    graph = graphBuilder.build()
  }

  private fun walkFileDeps(root: ResolvedComponentResult, conf: Configuration) {
    val rootId = root.id.toCoordinates()
    graphBuilder.addNode(rootId)

    // the only way to get flat jar file dependencies
    conf.allDependencies
      .filterIsInstance<FileCollectionDependency>()
      .mapNotNullToSet { it.toCoordinates() }
      .forEach { id ->
        graphBuilder.putEdge(rootId, id)
      }
  }

  private fun walk(root: ResolvedComponentResult) {
    val rootId = root.id.toCoordinates()

    root.dependencies
      .filterIsInstance<ResolvedDependencyResult>()
      // AGP adds all runtime dependencies as constraints to the compile classpath, and these show
      // up in the resolution result. Filter them out.
      .filterNot { it.isConstraint }
      // For similar reasons as above
      .filterNot { it.isJavaPlatform() }
      // Sometimes there is a self-dependency?
      .filterNot { it.selected == root }
      .forEach { dependencyResult ->
        val depId = dependencyResult.selected.id.toCoordinates()

        // add an edge
        graphBuilder.putEdge(rootId, depId)

        if (!visited.contains(depId)) {
          visited.add(depId)
          // recursively walk the graph in a depth-first pattern
          walk(dependencyResult.selected)
        }
      }
  }
}

/**
 * Returns true if any of the variants are a kind of platform.
 * TODO this is duplicated in DependencyMisuseTask.
 */
private fun ResolvedDependencyResult.isJavaPlatform(): Boolean = selected.variants.any { variant ->
  val category = variant.attributes.getAttribute(CATEGORY)
  category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
}

/**
 * This is different than [org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE], which has type
 * `Category` (cf `String`).
 */
private val CATEGORY = Attribute.of("org.gradle.category", String::class.java)

// TODO move
@Suppress("UnstableApiUsage")
internal object GraphWriter {

  fun toDot(graph: Graph<Coordinates>) = buildString {
    val projectNodes = graph.nodes()
      .filterIsInstance<ProjectCoordinates>()
      .map { it.toString() }

    appendReproducibleNewLine("strict digraph DependencyGraph {")
    appendReproducibleNewLine("  ratio=0.6;")
    appendReproducibleNewLine("  node [shape=box];")
    projectNodes.forEach {
      appendReproducibleNewLine("\n  \"$it\" [style=filled fillcolor=\"#008080\"];")
    }

    graph.edges().forEach { edge ->
      val from = edge.nodeU()
      val to = edge.nodeV()
      val style =
        if (from is ProjectCoordinates && to is ProjectCoordinates) " [style=bold color=\"#FF6347\" weight=8]"
        else ""
      append("  \"$from\" -> \"$to\"$style;")
      append("\n")
    }
    append("}")
  }
}
