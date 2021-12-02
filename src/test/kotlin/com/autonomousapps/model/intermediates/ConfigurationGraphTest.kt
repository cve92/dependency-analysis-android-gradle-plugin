// Guava graph
@file:Suppress("UnstableApiUsage")

package com.autonomousapps.model.intermediates

import com.google.common.graph.Graphs
import org.junit.jupiter.api.Test

internal class ConfigurationGraphTest {

  @Test fun test() {
    val graph = ConfigurationGraph.newGraphBuilder()
      .putEdge("api", "debugApi")
      .putEdge("api", "releaseApi")
      .putEdge("debugApi", "flavor1DebugApi")
      .putEdge("debugApi", "flavor2DebugApi")
      .putEdge("releaseApi", "flavor1DebugApi")
      .putEdge("releaseApi", "flavor2DebugApi")
      .putEdge("api", "implementation")
      .putEdge("implementation", "debugImplementation")
      .putEdge("implementation", "releaseImplementation")
      .putEdge("debugImplementation", "flavor1DebugImplementation")
      .putEdge("debugImplementation", "flavor2DebugImplementation")
      .build()
    val configurationGraph = ConfigurationGraph(
      projectPath = "",
      graph = graph
    )

    graph.predecessors("")
    val transpose = Graphs.transpose(graph)
    val closure = Graphs.transitiveClosure(graph)
    val p1 = closure.predecessors("flavor2DebugImplementation")
    val p2 = closure.predecessors("flavor2DebugImplementation")

  }
}
