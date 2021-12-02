package com.autonomousapps.model.intermediates

import com.autonomousapps.model.Coordinates

internal data class DependencyUsageReport(
  val variant: String,
  val abiDependencies: Set<Trace>,
  val implDependencies: Set<Trace>,
  val compileOnlyDependencies: Set<Trace>,
  val runtimeOnlyDependencies: Set<Trace>,
  val compileOnlyApiDependencies: Set<Trace>,
) {

  data class Trace(
    val coordinates: Coordinates,
    /** Any given dependency might be associated with 1+ reasons. */
    val reasons: Set<Reason>
  ) : Comparable<Trace> {
    override fun compareTo(other: Trace): Int = coordinates.compareTo(other.coordinates)
  }

  enum class Reason {
    ABI,
    COMPILE_ONLY,
    CONSTANT,
    IMPL,
    IMPORTED,
    INLINE,
    LINT_JAR,
    NATIVE_LIB,
    RES_BY_SRC,
    RES_BY_RES,
    RUNTIME_ANDROID,
    SECURITY_PROVIDER,
    SERVICE_LOADER,
  }
}
