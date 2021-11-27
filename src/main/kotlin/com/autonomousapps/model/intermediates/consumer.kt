package com.autonomousapps.model.intermediates

/** A single source file (e.g., `.java`, `.kt`) in this project. */
internal data class ExplodingSourceCode(
  val relativePath: String,
  val className: String,
  val kind: Kind,
  val imports: Set<String>
) : Comparable<ExplodingSourceCode> {

  override fun compareTo(other: ExplodingSourceCode): Int = relativePath.compareTo(other.relativePath)

  enum class Kind {
    JAVA,
    KOTLIN,
  }
}

internal data class ExplodingBytecode(
  val relativePath: String,
  val className: String,
  val sourceFile: String?,
  /** Every class discovered in the bytecode of [className]. */
  val usedClasses: Set<String>
)

internal data class ExplodingAbi(
  val className: String,
  val sourceFile: String?,
  /** Every class discovered in the bytecode of [className], and which is exposed as part of the ABI. */
  val exposedClasses: Set<String>
) : Comparable<ExplodingAbi> {
  override fun compareTo(other: ExplodingAbi): Int = className.compareTo(other.className)
}
