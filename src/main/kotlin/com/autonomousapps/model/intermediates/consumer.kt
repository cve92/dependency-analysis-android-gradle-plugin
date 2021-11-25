package com.autonomousapps.model.intermediates

internal data class ExplodingByteCode(
  val relativePath: String,
  val className: String,
  val sourceFile: String?,
  val usedClasses: Set<String>
)

internal data class ExplodingAbi(
  val className: String,
  val sourceFile: String?,
  val exposedClasses: Set<String>
) : Comparable<ExplodingAbi> {
  override fun compareTo(other: ExplodingAbi): Int = className.compareTo(other.className)
}
