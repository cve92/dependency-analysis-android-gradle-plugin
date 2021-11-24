package com.autonomousapps.model.intermediates

/**
 * A dependency's "location" is the configuration that it's connected to. A dependency may actually
 * be connected to more than one configuration, and that would not be an error.
 */
data class Location(
  val identifier: String,
  val configurationName: String,
  val attributes: Set<Attribute> = emptySet()
)

enum class Attribute {
  JAVA_PLATFORM
}
