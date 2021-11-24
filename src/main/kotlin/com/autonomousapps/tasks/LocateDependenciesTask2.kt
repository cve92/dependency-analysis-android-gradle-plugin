package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.NoVariantOutputPaths
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.toIdentifiers
import com.autonomousapps.internal.utils.toJson
import com.autonomousapps.model.intermediates.Attribute
import com.autonomousapps.model.intermediates.Location
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
abstract class LocateDependenciesTask2 : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Produces a report of all dependencies and the configurations on which they are declared"
  }

  @get:Nested
  abstract val locationContainer: Property<LocationContainer>

  /*
   * Outputs
   */

  @get:OutputFile
  abstract val output: RegularFileProperty

  companion object {
    internal fun configureTask(
      task: LocateDependenciesTask2,
      project: Project,
      outputPaths: NoVariantOutputPaths
    ) {
      task.locationContainer.set(computeLocations(project.configurations))
      task.output.set(outputPaths.locationsPath)
    }

    private fun computeLocations(configurations: ConfigurationContainer): LocationContainer {
      val metadata = mutableMapOf<String, Boolean>()
      return LocationContainer.of(
        mapping = configurations.asMap.asSequence()
          .filter { (_, conf) ->
            // we want dependency buckets only
            !conf.isCanBeConsumed && !conf.isCanBeResolved
          }
          .map { (name, conf) ->
            name to conf.dependencies.toIdentifiers(metadata)
          }.toMap(),
        metadata = LocationMetadata.of(metadata)
      )
    }
  }

  @TaskAction fun action() {
    val outputFile = output.getAndDelete()
    val locations = Locator(locationContainer.get()).locations()
    outputFile.writeText(locations.toJson())
  }
}

class LocationContainer(
  @get:Input
  val mapping: Map<String, Set<String>>,
  @get:Nested
  val metadata: LocationMetadata
) {

  companion object {
    fun of(
      mapping: Map<String, Set<String>>,
      metadata: LocationMetadata
    ): LocationContainer = LocationContainer(mapping, metadata)
  }
}

class LocationMetadata(
  @get:Input
  val metadata: Map<String, Boolean>
) {

  fun attributes(id: String): Set<Attribute> {
    return if (isJavaPlatform(id)) setOf(Attribute.JAVA_PLATFORM) else emptySet()
  }

  private fun isJavaPlatform(id: String): Boolean = metadata.containsKey(id)

  companion object {
    fun of(metadata: Map<String, Boolean>): LocationMetadata = LocationMetadata(metadata)
  }
}

// TODO unit test candidate
internal class Locator(private val locationContainer: LocationContainer) {

  fun locations(): Set<Location> {
    return locationContainer.mapping.asSequence()
      .filter { (name, _) -> isMain(name) || isAnnotationProcessor(name) }
      .flatMap { (conf, identifiers) ->
        identifiers.map { id ->
          Location(
            identifier = id,
            configurationName = conf,
            attributes = locationContainer.metadata.attributes(id)
          )
        }
      }
      .toSet()
  }

  companion object {
    private val MAIN_SUFFIXES = listOf(
      "api", "implementation", "compileOnly", "runtimeOnly",
    )
    private val ANNOTATION_PROCESSOR_PREFIXES = listOf(
      "kapt", "annotationProcessor",
    )

    private fun isMain(configurationName: String): Boolean {
      return MAIN_SUFFIXES.any { suffix -> configurationName.endsWith(suffix = suffix, ignoreCase = true) }
    }

    private fun isAnnotationProcessor(configurationName: String): Boolean {
      return ANNOTATION_PROCESSOR_PREFIXES.any { prefix -> configurationName.startsWith(prefix) }
    }
  }
}
