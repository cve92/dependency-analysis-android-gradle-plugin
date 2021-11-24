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
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@CacheableTask
abstract class SourceExploderTask @Inject constructor(
  private val workerExecutor: WorkerExecutor,
  private val layout: ProjectLayout
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Parses Java and Kotlin source to detect source-only usages"
  }

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

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(SourceExploderWorkAction::class.java) {
      projectDir.set(layout.projectDirectory)
      javaSourceFiles.setFrom(this@SourceExploderTask.javaSourceFiles)
      kotlinSourceFiles.setFrom(this@SourceExploderTask.kotlinSourceFiles)
      output.set(this@SourceExploderTask.output)
    }
  }
}

interface SourceExploderParameters : WorkParameters {
  val projectDir: DirectoryProperty
  val javaSourceFiles: ConfigurableFileCollection
  val kotlinSourceFiles: ConfigurableFileCollection
  val output: RegularFileProperty
}

abstract class SourceExploderWorkAction : WorkAction<SourceExploderParameters> {

  private val logger = getLogger<SourceExploderTask>()

  override fun execute() {
    // Output
    val reportFile = parameters.output.getAndDelete()

    val explodedSource = SourceExploder(
      projectDir = parameters.projectDir.get().asFile,
      javaSourceFiles = parameters.javaSourceFiles,
      kotlinSourceFiles = parameters.kotlinSourceFiles
    ).find()

    logger.info("Imports: $explodedSource")
    reportFile.writeText(explodedSource.toJson())
  }
}

private class SourceExploder(
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

  private fun walkTree(parser: SimpleParser): SourceListener {
    val tree = parser.file()
    val walker = ParseTreeWalker()
    val importListener = SourceListener()
    walker.walk(importListener, tree)
    return importListener
  }
}

private class SourceListener : SimpleBaseListener() {

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
