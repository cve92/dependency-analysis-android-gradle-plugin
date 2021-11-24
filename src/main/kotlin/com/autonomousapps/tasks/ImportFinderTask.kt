@file:Suppress("UnstableApiUsage")

package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.Imports
import com.autonomousapps.internal.SourceType
import com.autonomousapps.internal.antlr.v4.runtime.CharStreams
import com.autonomousapps.internal.antlr.v4.runtime.CommonTokenStream
import com.autonomousapps.internal.antlr.v4.runtime.tree.ParseTreeWalker
import com.autonomousapps.internal.grammar.SimpleBaseListener
import com.autonomousapps.internal.grammar.SimpleLexer
import com.autonomousapps.internal.grammar.SimpleParser
import com.autonomousapps.internal.utils.getAndDelete
import com.autonomousapps.internal.utils.getLogger
import com.autonomousapps.internal.utils.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Parses this project's source and extracts all import statements. The output contains the list of all source by type,
 * including only Java and Kotlin source at this time.
 */
@CacheableTask
abstract class ImportFinderTask @Inject constructor(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Produces a report of imports present in Java and Kotlin source"
  }

  // TODO make an input
  private val projectDir = project.layout.projectDirectory

  /**
   * The Java source of the current project.
   */
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val javaSourceFiles: ConfigurableFileCollection

  /**
   * The Kotlin source of the current project.
   */
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val kotlinSourceFiles: ConfigurableFileCollection

  /**
   * A [`Set<Imports>`][Imports] of imports present in project source. This set has two elements, one for Java and one
   * for Kotlin source.
   */
  @get:OutputFile
  abstract val importsReport: RegularFileProperty

  @TaskAction
  fun action() {
    workerExecutor.noIsolation().submit(ImportFinderWorkAction::class.java) {
      projectDir.set(this@ImportFinderTask.projectDir)
      javaSourceFiles.setFrom(this@ImportFinderTask.javaSourceFiles)
      kotlinSourceFiles.setFrom(this@ImportFinderTask.kotlinSourceFiles)
      constantUsageReport.set(this@ImportFinderTask.importsReport)
    }
  }
}

interface ImportFinderParameters : WorkParameters {
  val projectDir: DirectoryProperty
  val javaSourceFiles: ConfigurableFileCollection
  val kotlinSourceFiles: ConfigurableFileCollection
  val constantUsageReport: RegularFileProperty
}

abstract class ImportFinderWorkAction : WorkAction<ImportFinderParameters> {

  private val logger = getLogger<ImportFinderTask>()

  override fun execute() {
    // Output
    val reportFile = parameters.constantUsageReport.getAndDelete()

    val imports = ImportFinder(
      projectDir = parameters.projectDir.get().asFile,
      javaSourceFiles = parameters.javaSourceFiles,
      kotlinSourceFiles = parameters.kotlinSourceFiles
    ).find()

    logger.info("Imports: $imports")
    reportFile.writeText(imports.toJson())
  }
}

internal class ImportFinder(
  private val projectDir: File,
  private val javaSourceFiles: ConfigurableFileCollection,
  private val kotlinSourceFiles: ConfigurableFileCollection
) {
  fun find(): Set<Imports> {
    val javaImports = Imports(
      SourceType.JAVA, javaSourceFiles.associate { parseSourceFileForImports(it) }
    )
    val kotlinImports = Imports(
      SourceType.KOTLIN, kotlinSourceFiles.associate { parseSourceFileForImports(it) }
    )
    return setOf(javaImports, kotlinImports)
  }

  private fun parseSourceFileForImports(file: File): Pair<String, Set<String>> {
    val parser = newSimpleParser(file)
    val importListener = walkTree(parser)
    return file.toRelativeString(projectDir) to importListener.imports()
  }

  private fun newSimpleParser(file: File): SimpleParser {
    val input = FileInputStream(file).use { fis -> CharStreams.fromStream(fis) }
    val lexer = SimpleLexer(input)
    val tokens = CommonTokenStream(lexer)
    return SimpleParser(tokens)
  }

  private fun walkTree(parser: SimpleParser): SimpleImportListener {
    val tree = parser.file()
    val walker = ParseTreeWalker()
    val importListener = SimpleImportListener()
    walker.walk(importListener, tree)
    return importListener
  }
}

private class SimpleImportListener : SimpleBaseListener() {

  private val imports = mutableSetOf<String>()

  fun imports(): Set<String> = imports

  override fun enterImportDeclaration(ctx: SimpleParser.ImportDeclarationContext) {
    val qualifiedName = ctx.qualifiedName().text
    val import = if (ctx.children.any { it.text == "*" }) {
      "$qualifiedName.*"
    } else {
      qualifiedName
    }

    imports.add(import)
  }
}
