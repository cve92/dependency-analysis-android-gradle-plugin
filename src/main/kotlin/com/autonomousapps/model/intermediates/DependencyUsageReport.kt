package com.autonomousapps.model.intermediates

import com.autonomousapps.model.Coordinates

internal data class DependencyUsageReport(
  val variant: String,
  val abiDependencies: Set<Coordinates>,
  val implDependencies: Set<Coordinates>,
  val compileOnlyDependencies: Set<Coordinates>,
  val runtimeOnlyDependencies: Set<Coordinates>,
  val compileOnlyApiDependencies: Set<Coordinates>,
)
