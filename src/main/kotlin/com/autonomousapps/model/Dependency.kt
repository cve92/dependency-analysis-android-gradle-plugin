package com.autonomousapps.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import java.io.File

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Dependency(
  open val coordinates: Coordinates,
  open val capabilities: List<Capability>,
  open val file: File
) : Comparable<Dependency> {
  override fun compareTo(other: Dependency): Int = coordinates.compareTo(other.coordinates)
}

@TypeLabel("project")
@JsonClass(generateAdapter = false)
data class ProjectDependency(
  override val coordinates: ProjectCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

@TypeLabel("module")
@JsonClass(generateAdapter = false)
data class ModuleDependency(
  override val coordinates: ModuleCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

@TypeLabel("flat")
@JsonClass(generateAdapter = false)
data class FlatDependency(
  override val coordinates: FlatCoordinates,
  override val capabilities: List<Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

@JsonClass(generateAdapter = false, generator = "sealed:type")
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
        else -> gav().compareTo(other.gav())
      }
    } else {
      if (other !is FlatCoordinates) 1 else gav().compareTo(other.gav())
    }
  }

  /** Group-artifact-version (GAV) string representation, as used in Gradle dependency declarations. */
  abstract fun gav(): String

  fun toFileName() = gav().replace(":", "__") + ".json"
}

@TypeLabel("project")
@JsonClass(generateAdapter = false)
data class ProjectCoordinates(
  override val identifier: String
) : Coordinates(identifier) {
  override fun gav(): String = identifier
}

@TypeLabel("module")
@JsonClass(generateAdapter = false)
data class ModuleCoordinates(
  override val identifier: String,
  val resolvedVersion: String
) : Coordinates(identifier) {
  override fun gav(): String = "$identifier:$resolvedVersion"
}

/** For dependencies that have no version information. They might be a flat file on disk, or e.g. "Gradle API". */
@TypeLabel("flat")
@JsonClass(generateAdapter = false)
data class FlatCoordinates(
  override val identifier: String
) : Coordinates(identifier) {
  override fun gav(): String = identifier
}
