// Graph
@file:Suppress("UnstableApiUsage")

package com.autonomousapps.model

import com.google.common.graph.Graph

class DependencyGraphView(
  val name: String,
  val kind: GraphKind,
  private val graph: Graph<Dependency>
) {
}

enum class GraphKind {
  COMPILE_TIME,
  RUNTIME
}
