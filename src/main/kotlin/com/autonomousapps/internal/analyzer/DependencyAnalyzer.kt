@file:Suppress("UnstableApiUsage")

package com.autonomousapps.internal.analyzer

import com.autonomousapps.services.InMemoryCache
import com.autonomousapps.tasks.*
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Abstraction for differentiating between android-app, android-lib, and java-lib projects.
 */
internal interface DependencyAnalyzer {
  /** E.g., `flavorDebug` */
  val variantName: String

  /** E.g., 'flavor' */
  val flavorName: String?

  /** E.g., 'debug' */
  val buildType: String?

  /** E.g., `FlavorDebug` */
  val variantNameCapitalized: String

  /** E.g., "compileClasspath", "debugCompileClasspath". */
  val compileConfigurationName: String

  /** E.g., "testCompileClasspath", "debugTestCompileClasspath". */
  val testCompileConfigurationName: String

  val attributeValueJar: String

  val kotlinSourceFiles: FileTree
  val javaSourceFiles: FileTree?
  val javaAndKotlinSourceFiles: FileTree?

  val isDataBindingEnabled: Boolean
  val isViewBindingEnabled: Boolean

  val testJavaCompileName: String
  val testKotlinCompileName: String

  fun registerCreateVariantFilesTask(): TaskProvider<out CreateVariantFiles>

  /**
   * This produces a report that lists all of the used classes (FQCN) in the project.
   */
  fun registerClassAnalysisTask(
    createVariantFiles: TaskProvider<out CreateVariantFiles>
  ): TaskProvider<out ClassAnalysisTask>

  fun registerManifestPackageExtractionTask(): TaskProvider<ManifestPackageExtractionTask>? = null

  fun registerAndroidResToSourceAnalysisTask(
    manifestPackageExtractionTask: TaskProvider<ManifestPackageExtractionTask>
  ): TaskProvider<AndroidResToSourceAnalysisTask>? = null

  fun registerAndroidResToResAnalysisTask(): TaskProvider<AndroidResToResToResAnalysisTask>? = null

  fun registerFindNativeLibsTask(
    locateDependenciesTask: TaskProvider<LocateDependenciesTask>
  ): TaskProvider<FindNativeLibsTask>? = null

  fun registerFindAndroidLintersTask(
    locateDependenciesTask: TaskProvider<LocateDependenciesTask>
  ): TaskProvider<FindAndroidLinters>? = null

  fun registerFindDeclaredProcsTask(
    inMemoryCacheProvider: Provider<InMemoryCache>,
    locateDependenciesTask: TaskProvider<LocateDependenciesTask>
  ): TaskProvider<FindDeclaredProcsTask>

  fun registerFindUnusedProcsTask(
    findDeclaredProcs: TaskProvider<FindDeclaredProcsTask>,
    importFinder: TaskProvider<ImportFinderTask>
  ): TaskProvider<FindUnusedProcsTask>

  /**
   * This is a no-op for `com.android.application` and JVM `application` projects (including
   * Spring Boot), since they have no meaningful ABI.
   */
  fun registerAbiAnalysisTask(
    analyzeJarTask: TaskProvider<AnalyzeJarTask>,
    abiExclusions: Provider<String>
  ): TaskProvider<AbiAnalysisTask>? = null
}

internal abstract class AbstractDependencyAnalyzer(
  protected val project: Project
) : DependencyAnalyzer {

  protected val testJavaCompile by lazy {
    try {
      project.tasks.named<JavaCompile>(testJavaCompileName)
    } catch (e: UnknownTaskException) {
      null
    }
  }

  protected val testKotlinCompile by lazy {
    try {
      project.tasks.named<KotlinCompile>(testKotlinCompileName)
    } catch (e: UnknownTaskException) {
      null
    } catch (e: NoClassDefFoundError) {
      null
    }
  }
}
