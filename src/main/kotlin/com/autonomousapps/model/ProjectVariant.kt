package com.autonomousapps.model

/**
 * TODO: not sure this API makes any sense
 */
data class ProjectVariant(
  val variant: String,
  val sources: List<Source>,
  val classpath: Set<Coordinates>
) {

  val usedClasses by lazy {
    sources.filterIsInstance<CodeSource>().flatMap {
      it.usedClasses
    }
  }
}
