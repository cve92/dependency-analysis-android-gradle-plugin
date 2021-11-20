package com.autonomousapps.model

import com.autonomousapps.internal.KtFile
import com.autonomousapps.internal.utils.toCoordinates
import org.gradle.api.artifacts.component.ComponentIdentifier
import java.io.File

internal interface HasCoordinates {
  val coordinates: Coordinates
}

/**
 * A dependency that includes a lint jar. (Which is maybe always named lint.jar?)
 *
 * Example registry: `nl.littlerobots.rxlint.RxIssueRegistry`.
 */
internal data class AndroidLinterDependency(
  override val coordinates: Coordinates,
  val lintRegistry: String
) : Comparable<AndroidLinterDependency>, HasCoordinates {
  override fun compareTo(other: AndroidLinterDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }
}

/**
 * Metadata from an Android manifest.
 */
internal data class AndroidManifestDependency(
  override val coordinates: Coordinates,
  /** The package name per `<manifest package="...">`. */
  val packageName: String,
  /** A map of component type to components. */
  val componentMap: Map<String, Set<String>>
) : Comparable<AndroidManifestDependency>, HasCoordinates {

  constructor(
    packageName: String,
    componentMap: Map<String, Set<String>>,
    componentIdentifier: ComponentIdentifier
  ) : this(
    packageName = packageName,
    componentMap = componentMap,
    coordinates = componentIdentifier.toCoordinates()
  )

  override fun compareTo(other: AndroidManifestDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }

  enum class Component(val tagName: String, val mapKey: String) {
    ACTIVITY("activity", "activities"),
    SERVICE("service", "services"),
    RECEIVER("receiver", "receivers"),
    PROVIDER("provider", "providers");

    val attrName = "android:name"

    companion object {
      internal fun of(mapKey: String): Component {
        return values().find {
          it.mapKey == mapKey
        } ?: error("Could not find Manifest.Component for $mapKey")
      }
    }
  }
}

internal data class AnnotationProcessorDependency(
  override val coordinates: Coordinates,
  val processor: String,
  val supportedAnnotationTypes: Set<String>
) : Comparable<AnnotationProcessorDependency>, HasCoordinates {

  constructor(
    processor: String,
    supportedAnnotationTypes: Set<String>,
    componentIdentifier: ComponentIdentifier
  ) : this(
    processor = processor, supportedAnnotationTypes = supportedAnnotationTypes,
    coordinates = componentIdentifier.toCoordinates()
  )

  override fun compareTo(other: AnnotationProcessorDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }
}

internal data class InlineMemberDependency(
  override val coordinates: Coordinates,
  val inlineMembers: Set<String>
) : Comparable<InlineMemberDependency>, HasCoordinates {
  override fun compareTo(other: InlineMemberDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }
}

internal data class NativeLibDependency(
  override val coordinates: Coordinates,
  val fileNames: Set<String>
) : Comparable<NativeLibDependency>, HasCoordinates {
  override fun compareTo(other: NativeLibDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }
}
internal data class PhysicalArtifact(
  override val coordinates: Coordinates,
  /** Physical artifact on disk; a jar file. */
  val file: File
) : Comparable<PhysicalArtifact>, HasCoordinates {

  override fun compareTo(other: PhysicalArtifact): Int {
    return coordinates.compareTo(other.coordinates)
  }

  companion object {
    internal fun of(
      componentIdentifier: ComponentIdentifier,
      file: File,
    ) = PhysicalArtifact(
      coordinates = componentIdentifier.toCoordinates(),
      file = file
    )
  }
}

internal data class ServiceLoaderDependency(
  override val coordinates: Coordinates,
  val providerFile: String,
  val providerClasses: Set<String>
) : Comparable<ServiceLoaderDependency>, HasCoordinates {

  constructor(
    providerFile: String,
    providerClasses: Set<String>,
    componentIdentifier: ComponentIdentifier
  ) : this(
    providerFile = providerFile,
    providerClasses = providerClasses,
    coordinates = componentIdentifier.toCoordinates()
  )

  override fun compareTo(other: ServiceLoaderDependency): Int {
    return coordinates.compareTo(other.coordinates)
  }
}

/**
 * A library or project, along with the set of classes declared by, and other information contained within, this
 * exploded jar. This is the serialized form of [ExplodingJar].
 */
internal data class ExplodedJar(

  override val coordinates: Coordinates,

  /**
   * True if this dependency contains only annotation that are only needed at compile-time (`CLASS`
   * and `SOURCE` level retention policies). False otherwise.
   */
  val isCompileOnlyAnnotations: Boolean = false,
  /**
   * The set of classes that are service providers (they extend [java.security.Provider]). May be
   * empty.
   */
  val securityProviders: Set<String> = emptySet(),
  /**
   * Android Lint registry, if there is one. May be null.
   */
  val androidLintRegistry: String? = null,
  /**
   * True if this component contains _only_ an Android Lint jar/registry. If this is true,
   * [androidLintRegistry] must be non-null.
   */
  val isLintJar: Boolean = false,
  /**
   * The classes declared by this library.
   */
  val classes: Set<String>,
  /**
   * A map of each class declared by this library to the set of constants it defines. The latter may
   * be empty for any given declared class.
   */
  val constantFields: Map<String, Set<String>>,
  /**
   * All of the "Kt" files within this component.
   */
  val ktFiles: List<KtFile>
) : Comparable<ExplodedJar>, HasCoordinates {

  internal constructor(
    artifact: PhysicalArtifact,
    exploding: ExplodingJar
  ) : this(
    coordinates = artifact.coordinates,
    isCompileOnlyAnnotations = exploding.isCompileOnlyCandidate,
    securityProviders = exploding.securityProviders,
    androidLintRegistry = exploding.androidLintRegistry,
    isLintJar = exploding.isLintJar,
    classes = exploding.classNames,
    constantFields = exploding.constants,
    ktFiles = exploding.ktFiles
  )

  init {
    if (isLintJar && androidLintRegistry == null) {
      throw IllegalStateException("Android lint jar for $coordinates must contain a lint registry")
    }
  }

  override fun compareTo(other: ExplodedJar): Int = coordinates.compareTo(other.coordinates)
}

data class Res(
  override val coordinates: Coordinates,
  /** An import that indicates a possible use of an Android resource from this dependency. */
  val import: String,
  val lines: List<Line>
) : Comparable<Res>, HasCoordinates {

  constructor(
    componentIdentifier: ComponentIdentifier,
    import: String,
    lines: List<Line>
  ) : this(
    coordinates = componentIdentifier.toCoordinates(),
    import = import,
    lines = lines
  )

  override fun compareTo(other: Res): Int {
    return coordinates.compareTo(other.coordinates)
  }

  data class Line(val type: String, val value: String)
}
