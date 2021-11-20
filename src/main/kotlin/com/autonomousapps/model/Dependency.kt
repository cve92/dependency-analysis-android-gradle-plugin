package com.autonomousapps.model

import java.io.File

sealed class Dependency(
  open val coordinates: Coordinates,
  open val capabilities: List<Capability>,
  open val file: File
) : Comparable<Dependency> {
  override fun compareTo(other: Dependency): Int = coordinates.compareTo(other.coordinates)
}

data class ProjectDependency(
  override val coordinates: ProjectCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

data class ModuleDependency(
  override val coordinates: ModuleCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

data class FlatDependency(
  override val coordinates: FlatCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

sealed class Coordinates(
  open val identifier: String
) : Comparable<Coordinates> {

  /** [ProjectCoordinates] come before [ModuleCoordinates], which come before [FlatCoordinates]. */
  override fun compareTo(other: Coordinates): Int {
    return if (this is ProjectCoordinates) {
      if (other !is ProjectCoordinates) 1 else identifier.compareTo(other.identifier)
    } else if (this is ModuleCoordinates) {
      when (other) {
        is ProjectCoordinates -> -1
        is FlatCoordinates -> 1
        else -> toString().compareTo(other.toString())
      }
    } else {
      if (other !is FlatCoordinates) 1 else toString().compareTo(other.toString())
    }
  }
}

data class ProjectCoordinates(
  override val identifier: String
) : Coordinates(identifier) {
  override fun toString(): String = identifier
}

data class ModuleCoordinates(
  override val identifier: String,
  val resolvedVersion: String
) : Coordinates(identifier) {
  override fun toString(): String = "$identifier:$resolvedVersion"
}

/** For dependencies that have no version information. They might be a flat file on disk, or e.g. "Gradle API". */
data class FlatCoordinates(
  override val identifier: String
) : Coordinates(identifier) {
  override fun toString(): String = identifier
}
