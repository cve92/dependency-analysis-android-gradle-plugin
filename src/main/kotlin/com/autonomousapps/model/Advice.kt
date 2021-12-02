package com.autonomousapps.model

import com.autonomousapps.internal.utils.isTrue

data class Advice(
  /** The coordinates of the dependency that ought to be modified in some way. */
  val coordinates: Coordinates,
  /** The current configuration on which the dependency has been declared. Will be null for transitive dependencies. */
  val fromConfiguration: String? = null,
  /**
   * The configuration on which the dependency _should_ be declared. Will be null if the dependency is unused and
   * therefore ought to be removed.
   */
  val toConfiguration: String? = null
) : Comparable<Advice> {

  override fun compareTo(other: Advice): Int {
    val depComp = coordinates.compareTo(other.coordinates)
    // If dependencies are non-equal, sort by them alone
    if (depComp != 0) return depComp

    if (toConfiguration == null && other.toConfiguration == null) return 0
    // If this toConfiguration is null, prefer this
    if (toConfiguration == null) return 1
    // If other.toConfiguration is null, prefer that
    if (other.toConfiguration == null) return -1

    val toConfComp = toConfiguration.compareTo(other.toConfiguration)
    // If toConfigurations are non-equal, sort by them alone
    if (toConfComp != 0) return toConfComp

    if (fromConfiguration == null && other.fromConfiguration == null) return 0
    // If this fromConfiguration is null, prefer this
    if (fromConfiguration == null) return 1
    // If other.fromConfiguration is null, prefer that
    if (other.fromConfiguration == null) return -1

    // If no fromConfiguration is null, sort by natural string ordering
    return fromConfiguration.compareTo(other.fromConfiguration)
  }

  companion object {
    @JvmStatic
    fun ofAdd(coordinates: Coordinates, toConfiguration: String) = Advice(
      coordinates = coordinates,
      fromConfiguration = null,
      toConfiguration = toConfiguration
    )

    @JvmStatic
    fun ofRemove(coordinates: Coordinates, fromConfiguration: String) = Advice(
      coordinates = coordinates,
      fromConfiguration = fromConfiguration, toConfiguration = null
    )

    @JvmStatic
    fun ofChange(coordinates: Coordinates, fromConfiguration: String, toConfiguration: String) = Advice(
      coordinates = coordinates,
      fromConfiguration = fromConfiguration,
      toConfiguration = toConfiguration
    )
  }

  /**
   * `compileOnly` dependencies are special. If they are so declared, we assume the user knows what
   * they're doing and do not recommend changing them. We also don't recommend _adding_ a
   * compileOnly dependency that is only included transitively (to be less annoying).
   *
   * So, an advice is "compileOnly-advice" only if it is a compileOnly candidate and is declared on
   * a different configuration.
   */
  fun isCompileOnly() = toConfiguration?.endsWith("compileOnly", ignoreCase = true) == true

  /**
   * An advice is "add-advice" if it is undeclared and used, AND is not `compileOnly`.
   */
  fun isAdd() = fromConfiguration == null && toConfiguration != null && !isCompileOnly()

  /**
   * An advice is "remove-advice" if it is declared and not used, AND is not `compileOnly`,
   * AND is not `processor`.
   */
  fun isRemove() = toConfiguration == null && !isCompileOnly() && !isProcessor()

  /**
   * An advice is "change-advice" if it is declared and used (but is on the wrong configuration),
   * AND is not `compileOnly`.
   */
  fun isChange() = fromConfiguration != null && toConfiguration != null && !isCompileOnly()

  /**
   * An advice is "processors-advice" if it is declared on a k/apt or annotationProcessor
   * configuration, and this dependency should be removed.
   */
  fun isProcessor() = toConfiguration == null && fromConfiguration?.let {
    it.endsWith("kapt", ignoreCase = true) || it.endsWith("annotationProcessor", ignoreCase = true)
  }.isTrue()

  /** If this is advice to remove or downgrade an api-like dependency. */
  fun isDowngrade(): Boolean {
    return (isRemove() || isChange() || isCompileOnly())
      && fromConfiguration?.endsWith("api", ignoreCase = true) == true
  }

  fun isToApiLike(): Boolean = toConfiguration?.endsWith("api", ignoreCase = true) == true
}
