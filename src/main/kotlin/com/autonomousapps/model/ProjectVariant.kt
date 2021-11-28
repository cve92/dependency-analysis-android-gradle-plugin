package com.autonomousapps.model

import com.autonomousapps.internal.utils.flatMapToSet

/**
 * TODO: not sure this API makes any sense
 */
data class ProjectVariant(
  val variant: String,
  val sources: Set<Source>,
  val classpath: Set<Coordinates>
) {

  // TODO @Transient?
  val usedClasses: Set<String> by lazy {
    sources.filterIsInstance<CodeSource>().flatMapToSet {
      it.usedClasses
    }
  }
}
