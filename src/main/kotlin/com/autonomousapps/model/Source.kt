package com.autonomousapps.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Source(
  /**
   * Source file path relative to project dir (e.g. `src/main/com/foo/Bar.kt`).
   */
  open val relativePath: String
) : Comparable<Source> {

  override fun compareTo(other: Source): Int = when (this) {
    is CodeSource -> {
      if (other is AndroidResSource) 1 else relativePath.compareTo(other.relativePath)
    }
    is AndroidResSource -> {
      if (other is CodeSource) -1 else relativePath.compareTo(other.relativePath)
    }
  }
}

@TypeLabel("code")
@JsonClass(generateAdapter = false)
data class CodeSource(
  override val relativePath: String,
  val kind: Kind,
  val imports: Set<String>
) : Source(relativePath) {

  enum class Kind {
    JAVA,
    KOTLIN,
  }
}

@TypeLabel("android_res")
@JsonClass(generateAdapter = false)
class AndroidResSource(
  override val relativePath: String
) : Source(relativePath) {

}
