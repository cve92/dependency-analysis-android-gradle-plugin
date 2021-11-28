package com.autonomousapps.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel
import java.io.File

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Dependency(
  open val coordinates: Coordinates,
  open val capabilities: Map<String, Capability>,
  open val file: File
) : Comparable<Dependency> {
  override fun compareTo(other: Dependency): Int = coordinates.compareTo(other.coordinates)

  // @Suppress("UNCHECKED_CAST")
  // fun <T : Capability> capabilityOf(type: Class<T>): T? = capabilities[type.canonicalName] as T?

  inline fun <reified T : Capability> capabilityOf(): T? = capabilities[T::class.java.canonicalName] as T?
}

@TypeLabel("project")
@JsonClass(generateAdapter = false)
data class ProjectDependency(
  override val coordinates: ProjectCoordinates,
  /** Map of [Capability] canonicalName to the capability. */
  override val capabilities: Map<String, Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

@TypeLabel("module")
@JsonClass(generateAdapter = false)
data class ModuleDependency(
  override val coordinates: ModuleCoordinates,
  override val capabilities: Map<String, Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)

@TypeLabel("flat")
@JsonClass(generateAdapter = false)
data class FlatDependency(
  override val coordinates: FlatCoordinates,
  override val capabilities: Map<String, Capability>,
  override val file: File
) : Dependency(coordinates, capabilities, file)
