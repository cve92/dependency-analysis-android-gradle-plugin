package com.autonomousapps.model

import com.autonomousapps.internal.utils.flatMapToOrderedSet
import com.autonomousapps.internal.utils.flatMapToSet
import com.autonomousapps.model.CodeSource.Kind

/**
 *
 */
data class ProjectVariant(
  val coordinates: ProjectCoordinates,
  val variant: String,
  val sources: Set<Source>,
  val classpath: Set<Coordinates>
) {

  val usedClasses: Set<String> by lazy {
    codeSource.flatMapToSet {
      it.usedClasses
    }
  }

  val exposedClasses: Set<String> by lazy {
    codeSource.flatMapToSet {
      it.exposedClasses
    }
  }

  val implementationClasses: Set<String> by lazy {
    usedClasses - exposedClasses
  }

  val codeSource: List<CodeSource> by lazy {
    sources.filterIsInstance<CodeSource>()
  }

  val androidResSource: List<AndroidResSource> by lazy {
    sources.filterIsInstance<AndroidResSource>()
  }

  val javaImports: Set<String> by lazy {
    codeSource.filter { it.kind == Kind.JAVA }
      .flatMapToOrderedSet { it.imports }
  }

  val kotlinImports: Set<String> by lazy {
    codeSource.filter { it.kind == Kind.KOTLIN }
      .flatMapToOrderedSet { it.imports }
  }

  val imports: Set<String> by lazy {
    sortedSetOf<String>().apply {
      addAll(javaImports)
      addAll(kotlinImports)
    }
  }
}
