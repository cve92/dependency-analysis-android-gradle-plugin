// Guava Graph
@file:Suppress("UnstableApiUsage")

package com.autonomousapps.model.intermediates

import com.autonomousapps.internal.configuration.Configurations
import com.autonomousapps.tasks.SerializableConfigurationGraph
import com.autonomousapps.tasks.SerializableConfigurationGraph.SerializableEdge
import com.google.common.graph.ElementOrder
import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import org.gradle.api.artifacts.Configuration

internal class ConfigurationGraph(
  val projectPath: String,
  val graph: Graph<String>
) {

  companion object {
    fun newGraphBuilder(): ImmutableGraph.Builder<String> {
      return GraphBuilder.directed()
        .allowsSelfLoops(false)
        .incidentEdgeOrder(ElementOrder.stable<String>())
        .immutable()
    }

    fun of(graph: SerializableConfigurationGraph): ConfigurationGraph {
      val builder = newGraphBuilder()
      graph.edges.forEach {
        builder.putEdge(it.source, it.target)
      }
      return ConfigurationGraph(graph.projectPath, builder.build())
    }
  }

  fun serializableEdges(): Set<SerializableEdge> {
    return graph.edges().asSequence()
      .map { SerializableEdge(it.source(), it.target()) }
      .toSet()
  }

  class Builder(val projectPath: String) {

    private val builder = newGraphBuilder()
    private val visited = mutableMapOf<String, Boolean>()

    fun build(buckets: Sequence<Configuration>): ConfigurationGraph {
      buckets.forEach { bucket ->
        visit(bucket)
      }
      return ConfigurationGraph(projectPath, builder.build())
    }

    private fun visit(bucket: Configuration) {
      if (visited(bucket)) return

      val ancestors = bucket.extendsFrom
      ancestors.forEach { ancestor ->
        builder.putEdge(ancestor.name, bucket.name)
        visit(ancestor)
      }

      if (ancestors.isEmpty() && Configurations.isVariant(bucket.name)) {
        Configurations.findMain(bucket.name)?.let { main ->
          builder.putEdge(main, bucket.name)
        }
      }
    }

    private fun visited(bucket: Configuration): Boolean {
      return visited.putIfAbsent(bucket.name, true) == true
    }
  }
}
