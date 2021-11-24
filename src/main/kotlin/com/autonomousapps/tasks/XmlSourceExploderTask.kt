package com.autonomousapps.tasks

import com.autonomousapps.TASK_GROUP_DEP_INTERNAL
import com.autonomousapps.internal.utils.*
import com.autonomousapps.model.AndroidResSource
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.w3c.dom.Document
import java.io.File
import javax.inject.Inject

/**
 * TODO this kdoc is out of date.
 *
 * This task takes two inputs:
 * 1. Android res files declared by this project (xml)
 * 2. artifacts of type "android-public-res" (public.txt)
 *
 * We can parse the first for elements that might be present in the second. For example, if we have
 * ```
 * <resources>
 *   <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
 * </resources>
 * ```
 * we can expect to find, in public.txt, this line, associated with the dependency that supplies it (in this case
 * `'androidx.appcompat:appcompat'`):
 * ```
 * style Theme_AppCompat_Light_DarkActionBar
 * ```
 */
@CacheableTask
abstract class XmlSourceExploderTask @Inject constructor(
  private val workerExecutor: WorkerExecutor,
  private val layout: ProjectLayout
) : DefaultTask() {

  init {
    group = TASK_GROUP_DEP_INTERNAL
    description = "Produces a report of all resources references in this project"
  }

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  abstract val androidLocalRes: ConfigurableFileCollection

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    workerExecutor.noIsolation().submit(XmlSourceExploderWorkAction::class.java) {
      projectDir.set(layout.projectDirectory)
      xml.setFrom(androidLocalRes)
      output.set(this@XmlSourceExploderTask.output)
    }
  }
}

interface XmlSourceExploderParameters : WorkParameters {
  val projectDir: DirectoryProperty
  val xml: ConfigurableFileCollection
  val output: RegularFileProperty
}

abstract class XmlSourceExploderWorkAction : WorkAction<XmlSourceExploderParameters> {

  override fun execute() {
    val output = parameters.output.getAndDelete()

    val androidResSource = AndroidResParser(
      parameters.projectDir.get().asFile,
      parameters.xml
    ).androidResSource

    output.writeText(androidResSource.toJson())
  }
}

private class AndroidResParser(
  projectDir: File,
  resources: Iterable<File>
) {

  val androidResSource: Set<AndroidResSource> = resources
    .map { it to buildDocument(it) }
    .mapToOrderedSet { (file, doc) ->
      AndroidResSource(
        relativePath = file.toRelativeString(projectDir),
        styleParentRefs = extractStyleParentsFromResourceXml(doc),
        attrRefs = extractAttrsFromResourceXml(doc)
      )
    }

  // e.g., "Theme.AppCompat.Light.DarkActionBar"
  private fun extractStyleParentsFromResourceXml(doc: Document) =
    doc.getElementsByTagName("style").mapNotNull {
      it.attributes.getNamedItem("parent")?.nodeValue
    }.mapToSet {
      // Transform Theme.AppCompat.Light.DarkActionBar to Theme_AppCompat_Light_DarkActionBar
      it.replace('.', '_')
    }.mapToSet {
      AndroidResSource.StyleParentRef(it)
    }

  private fun extractAttrsFromResourceXml(doc: Document): Set<AndroidResSource.AttrRef> {
    return doc.attrs().entries.mapNotNullToSet { AndroidResSource.AttrRef.from(it) }
  }
}
