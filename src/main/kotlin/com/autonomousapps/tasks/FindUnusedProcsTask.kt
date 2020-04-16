package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP
import com.autonomousapps.internal.AnnotationProcessor
import com.autonomousapps.internal.Imports
import com.autonomousapps.internal.ProcClassVisitor
import com.autonomousapps.internal.asm.ClassReader
import com.autonomousapps.internal.utils.flatMapToSet
import com.autonomousapps.internal.utils.fromJsonList
import com.autonomousapps.internal.utils.fromJsonSet
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.File
import java.util.zip.ZipFile

@CacheableTask
abstract class FindUnusedProcsTask : DefaultTask() {

  init {
    group = TASK_GROUP_DEP
    description = "Produces a report of unused annotation processors"
  }

  /**
   * This project's compiled source.
   */
  @get:Optional
  @get:Classpath
  abstract val jar: RegularFileProperty

  /**
   * This project's compiled source. Class files generated by Kotlin source. May be empty.
   */
  @get:Classpath
  @get:InputFiles
  abstract val kotlinClasses: ConfigurableFileCollection

  /**
   * This project's compiled source. Class files generated by Java source. May be empty.
   */
  @get:Classpath
  @get:InputFiles
  abstract val javaClasses: ConfigurableFileCollection

  /**
   * All the imports in the Java and Kotlin source in this project. Needed for annotation processors
   * that support annotation types that have
   * [RetentionPolicy.SOURCE][java.lang.annotation.RetentionPolicy.SOURCE] or
   * [AnnotationRetention.SOURCE] retention.
   */
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFile
  abstract val imports: RegularFileProperty

  /**
   * Annotation processors that are present in the project (on `kapt` or `annotationProcessor`
   * configurations).
   */
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val annotationProcessorsProperty: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  private val annotationProcessors by lazy {
    annotationProcessorsProperty.get().asFile.readText().fromJsonSet<AnnotationProcessor>()
  }

  @TaskAction fun action() {
    // Output
    val outputFile = output.get().asFile
    outputFile.delete()

    // Inputs
    val classFiles = javaClasses.plus(kotlinClasses)
    val jarFile = jar.orNull?.asFile

    val a = findUsedProcsInClassFiles(classFiles)
    val b = jarFile?.let { findUsedProcsInJar(it) } ?: emptySet()
    val c = findUsedProcsInImports()
    val usedProcs = a + b + c
    val unusedProcs = annotationProcessors - usedProcs

    outputFile.writeText(unusedProcs.toJson())
  }

  /**
   * Because there is a 1-to-many relationship between dependencies and annotation processors (one dependency can
   * declare many annotation processors; see AutoValue for example), our set difference needs to consider _only_
   * the dependency itself.
   */
  private operator fun Set<AnnotationProcessor>.minus(other: Set<AnnotationProcessor>): Set<AnnotationProcessor> {
    // Initialize with full set
    val difference = mutableSetOf<AnnotationProcessor>().apply { addAll(this@minus) }
    // Now remove from set all matches in `other`
    for (proc in this) {
      if (other.any { it.dependency == proc.dependency }) {
        difference.remove(proc)
      }
    }
    return difference
  }

  private fun findUsedProcsInClassFiles(classFiles: FileCollection): Set<AnnotationProcessor> {
    return classFiles
      .filter { it.name.endsWith(".class") }
      .flatMapToSet { classFile ->
        val visitor = ProcClassVisitor(logger, annotationProcessors)
        val reader = classFile.inputStream().use { ClassReader(it.readBytes()) }
        reader.accept(visitor, 0)
        visitor.usedProcs()
      }
  }

  private fun findUsedProcsInJar(jarFile: File): Set<AnnotationProcessor> {
    val zip = ZipFile(jarFile)

    return zip.entries().toList()
      .filter { it.name.endsWith(".class") }
      .flatMapToSet { classEntry ->
        val visitor = ProcClassVisitor(logger, annotationProcessors)
        val reader = zip.getInputStream(classEntry).use { ClassReader(it.readBytes()) }
        reader.accept(visitor, 0)
        visitor.usedProcs()
      }
  }

  private fun findUsedProcsInImports(): Set<AnnotationProcessor> {
    val imports = imports.get().asFile.readText().fromJsonList<Imports>().flatten()

    val usedProcs = mutableSetOf<AnnotationProcessor>()
    for (proc in annotationProcessors) {
      if (proc.supportedAnnotationTypes.any { imports.contains(it) }) {
        usedProcs.add(proc)
      }
    }

    return usedProcs
  }

  private fun List<Imports>.flatten(): Set<String> {
    val destination = mutableSetOf<String>()
    for (i in this) {
      destination.addAll(i.imports)
    }
    return destination
  }
}