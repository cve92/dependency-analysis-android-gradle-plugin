package com.autonomousapps.model

import com.autonomousapps.internal.KtFile
import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Capability : Comparable<Capability> {
  override fun compareTo(other: Capability): Int = javaClass.simpleName.compareTo(other.javaClass.simpleName)
}

@TypeLabel("linter")
@JsonClass(generateAdapter = false)
data class AndroidLinterCapability(
  val lintRegistry: String,
  /** True if this dependency contains _only_ an Android lint jar/registry. */
  val isLintJar: Boolean
) : Capability()

@TypeLabel("manifest")
@JsonClass(generateAdapter = false)
data class AndroidManifestCapability(
  val packageName: String,
  val componentMap: Map<String, Set<String>>
) : Capability()

@TypeLabel("res")
@JsonClass(generateAdapter = false)
data class AndroidResCapability(
  val rImport: String,
  val lines: List<AndroidRes.Line>
) : Capability()

@TypeLabel("proc")
@JsonClass(generateAdapter = false)
data class AnnotationProcessorCapability(
  val processor: String,
  val supportedAnnotationTypes: Set<String>
) : Capability()

@TypeLabel("class")
@JsonClass(generateAdapter = false)
data class ClassCapability(
  val classes: Set<String>
) : Capability()

@TypeLabel("const")
@JsonClass(generateAdapter = false)
data class ConstantCapability(
  val constants: Map<String, Set<String>>
) : Capability()

@TypeLabel("inferred")
@JsonClass(generateAdapter = false)
data class InferredCapability(
  /**
   * True if this dependency contains only annotations that are only needed at compile-time (`CLASS`
   * and `SOURCE` level retention policies). False otherwise.
   */
  val isCompileOnlyAnnotations: Boolean
) : Capability()

@TypeLabel("inline")
@JsonClass(generateAdapter = false)
data class InlineMemberCapability(
  val inlineMembers: Set<String>
) : Capability()

// TODO not sure about this
@TypeLabel("kt_file")
@JsonClass(generateAdapter = false)
data class KtFileCapability(
  val ktFiles: List<KtFile>
) : Capability()

@TypeLabel("native")
@JsonClass(generateAdapter = false)
data class NativeLibCapability(
  val fileNames: Set<String>
) : Capability()

@TypeLabel("service_loader")
@JsonClass(generateAdapter = false)
data class ServiceLoaderCapability(
  val providerFile: String,
  val providerClasses: Set<String>
) : Capability()

@TypeLabel("security_provider")
@JsonClass(generateAdapter = false)
data class SecurityProviderCapability(
  val securityProviders: Set<String>
) : Capability()
