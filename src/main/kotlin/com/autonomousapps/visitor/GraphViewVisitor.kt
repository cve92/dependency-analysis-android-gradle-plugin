package com.autonomousapps.visitor

import com.autonomousapps.model.Dependency
import com.autonomousapps.model.DependencyGraphView
import com.autonomousapps.model.ProjectVariant
import com.autonomousapps.model.intermediates.Location

interface GraphViewVisitor {
  fun visit(dependency: Dependency, context: Context)

  interface Context {
    val project: ProjectVariant
    val dependencies: Set<Dependency>
    val graph: DependencyGraphView
    val locations: Set<Location>
  }
}
