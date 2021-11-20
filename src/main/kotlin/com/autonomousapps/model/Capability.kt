package com.autonomousapps.model

import com.autonomousapps.internal.KtFile

sealed class Capability : Comparable<Capability> {
  override fun compareTo(other: Capability): Int = javaClass.simpleName.compareTo(other.javaClass.simpleName)
}

data class AndroidLinterCapability(
  val lintRegistry: String,
  /** True if this dependency contains _only_ an Android lint jar/registry. */
  val isLintJar: Boolean
) : Capability()

data class AndroidManifestCapability(
  val packageName: String,
  val componentMap: Map<String, Set<String>>
) : Capability()

data class AndroidResCapability(
  val rImport: String,
  val lines: List<Res.Line>
) : Capability()

data class AnnotationProcessorCapability(
  val processor: String,
  val supportedAnnotationTypes: Set<String>
) : Capability()

data class ClassCapability(
  val classes: Set<String>
) : Capability()

data class ConstantCapability(
  val constants: Map<String, Set<String>>
) : Capability()

data class InferredCapability(
  /**
   * True if this dependency contains only annotations that are only needed at compile-time (`CLASS`
   * and `SOURCE` level retention policies). False otherwise.
   */
  val isCompileOnlyAnnotations: Boolean
) : Capability()

data class InlineMemberCapability(
  val inlineMembers: Set<String>
) : Capability()

// TODO not sure about this
data class KtFileCapability(
  val ktFiles: List<KtFile>
) : Capability()

data class NativeLibCapability(
  val fileNames: Set<String>
) : Capability()

data class ServiceLoaderCapability(
  val providerFile: String,
  val providerClasses: Set<String>
) : Capability()

data class SecurityProviderCapability(
  val securityProviders: Set<String>
) : Capability()
