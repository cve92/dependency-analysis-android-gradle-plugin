package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.graph.GraphWriter
import com.autonomousapps.internal.NoVariantOutputPaths
import com.autonomousapps.internal.configuration.Configurations.isAnnotationProcessor
import com.autonomousapps.internal.configuration.Configurations.isMain
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.toIdentifiers
import com.autonomousapps.internal.utils.toJson
import com.autonomousapps.model.intermediates.Attribute
import com.autonomousapps.model.intermediates.ConfigurationGraph
import com.autonomousapps.model.intermediates.Location
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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

  @get:Input
  abstract val projectPath: Property<String>

  @get:Nested
  abstract val locationContainer: Property<LocationContainer>

  @get:Nested
  abstract val serializableGraph: Property<SerializableConfigurationGraph>

  /*
   * Outputs
   */

  @get:OutputFile
  abstract val output: RegularFileProperty

  @get:OutputFile
  abstract val outputBuckets: RegularFileProperty

  @get:OutputFile
  abstract val outputBucketsDot: RegularFileProperty

  @TaskAction fun action() {
    val output = output.getAndDelete()
    val bucketOutput = outputBuckets.getAndDelete()
    val bucketDotOutput = outputBucketsDot.getAndDelete()

    val locations = Locator(locationContainer.get()).locations()
    val graph = ConfigurationGraph.of(serializableGraph.get())

    output.writeText(locations.toJson())
    bucketOutput.writeText(graph.toJson())
    bucketDotOutput.writeText(GraphWriter.toDot(graph))
  }

  companion object {
    internal fun configureTask(
      task: LocateDependenciesTask2,
      project: Project,
      outputPaths: NoVariantOutputPaths
    ) {
      task.projectPath.set(project.path)
      task.locationContainer.set(computeLocations(project.configurations))
      task.serializableGraph.set(computeSerializableConfigurationGraph(project))
      task.output.set(outputPaths.locationsPath)
      task.outputBuckets.set(outputPaths.dependencyBucketPath)
      task.outputBucketsDot.set(outputPaths.dependencyBucketDotPath)
    }

    private fun computeLocations(configurations: ConfigurationContainer): LocationContainer {
      val metadata = mutableMapOf<String, Boolean>()
      return LocationContainer.of(
        mapping = getDependencyBuckets(configurations)
          .associateBy { it.name }
          .map { (name, conf) ->
            name to conf.dependencies.toIdentifiers(metadata)
          }
          .toMap(),
        metadata = LocationMetadata.of(metadata)
      )
    }

    private fun computeSerializableConfigurationGraph(project: Project): SerializableConfigurationGraph {
      return SerializableConfigurationGraph.of(
        ConfigurationGraph.Builder(project.path).build(getDependencyBuckets(project.configurations))
      )
    }

    private fun getDependencyBuckets(configurations: ConfigurationContainer): Sequence<Configuration> {
      return configurations.asSequence()
        .filter { conf ->
          // we want dependency buckets only
          !conf.isCanBeConsumed && !conf.isCanBeResolved
        }
        .filter { isMain(it.name) || isAnnotationProcessor(it.name) }
    }
  }
}

class LocationContainer(
  @get:Input
  val mapping: Map<String, Set<String>>,
  @get:Nested
  val metadata: LocationMetadata
) {

  companion object {
    internal fun of(
      mapping: Map<String, Set<String>>,
      metadata: LocationMetadata
    ): LocationContainer = LocationContainer(mapping, metadata)
  }
}

class LocationMetadata(
  @get:Input
  val metadata: Map<String, Boolean>
) {

  internal fun attributes(id: String): Set<Attribute> {
    return if (isJavaPlatform(id)) setOf(Attribute.JAVA_PLATFORM) else emptySet()
  }

  private fun isJavaPlatform(id: String): Boolean = metadata.containsKey(id)

  companion object {
    internal fun of(metadata: Map<String, Boolean>): LocationMetadata = LocationMetadata(metadata)
  }
}

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
}

data class SerializableConfigurationGraph(
  @get:Input
  val projectPath: String,
  @get:Nested
  val edges: Set<SerializableEdge>
) {
  companion object {
    internal fun of(graph: ConfigurationGraph): SerializableConfigurationGraph {
      return SerializableConfigurationGraph(
        graph.projectPath,
        graph.serializableEdges()
      )
    }
  }

  data class SerializableEdge(
    @get:Input
    val source: String,
    @get:Input
    val target: String
  )
}
