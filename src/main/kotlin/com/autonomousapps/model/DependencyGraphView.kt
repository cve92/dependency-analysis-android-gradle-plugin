// Guava's Graph
@file:Suppress("UnstableApiUsage")

package com.autonomousapps.model

import com.google.common.graph.ElementOrder
import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph

/**
 * This is a metadata view of a [GraphKind] classpath. It expresses the relationship between the dependencies in the
 * classpath, as a dependency resolution engine (such as Gradle's) understands it. [name] is the Android variant, or the
 * JVM [SourceSet][org.gradle.api.tasks.SourceSet], that it represents.
 */
class DependencyGraphView(
  /** The variant (Android) or source set (JVM) name. */
  val name: String,
  val kind: GraphKind,
  internal val graph: Graph<Coordinates>
) {

  companion object {
    internal fun newGraphBuilder(): ImmutableGraph.Builder<Coordinates> {
      return GraphBuilder.directed()
        .allowsSelfLoops(false)
        .incidentEdgeOrder(ElementOrder.stable<Coordinates>())
        .immutable()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DependencyGraphView

    if (name != other.name) return false
    if (kind != other.kind) return false
    if (graph != other.graph) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + graph.hashCode()
    return result
  }
}

enum class GraphKind {
  COMPILE_TIME,
  RUNTIME,
}
