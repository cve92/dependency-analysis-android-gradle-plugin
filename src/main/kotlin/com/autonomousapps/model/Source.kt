package com.autonomousapps.model

import com.squareup.moshi.JsonClass
import dev.zacsweers.moshix.sealed.annotations.TypeLabel

@JsonClass(generateAdapter = false, generator = "sealed:type")
sealed class Source(
  /** Source file path relative to project dir (e.g. `src/main/com/foo/Bar.kt`). */
  open val relativePath: String
) : Comparable<Source> {

  override fun compareTo(other: Source): Int = when (this) {
    is RawCodeSource -> if (other !is RawCodeSource) 1 else defaultCompareTo(other)
    is AndroidResSource -> if (other !is AndroidResSource) -1 else defaultCompareTo(other)
    is ByteCodeSource -> {
      when (other) {
        is RawCodeSource -> -1
        !is ByteCodeSource -> 1
        else -> defaultCompareTo(other)
      }
    }
  }

  private fun defaultCompareTo(other: Source): Int = relativePath.compareTo(other.relativePath)
}

/** A single source file (e.g., `.java`, `.kt`) in this project. */
@TypeLabel("raw_code")
@JsonClass(generateAdapter = false)
data class RawCodeSource(
  override val relativePath: String,
  val kind: Kind,
  val imports: Set<String>
) : Source(relativePath) {

  enum class Kind {
    JAVA,
    KOTLIN,
  }
}

/** A single `.class` file in this project. */
@TypeLabel("bytecode")
@JsonClass(generateAdapter = false)
data class ByteCodeSource(
  override val relativePath: String,
  val className: String,
  val source: String?,
  val usedClasses: Set<String>
) : Source(relativePath)

/** A single XML file in this project. */
@TypeLabel("android_res")
@JsonClass(generateAdapter = false)
data class AndroidResSource(
  override val relativePath: String,
  val styleParentRefs: Set<StyleParentRef>,
  val attrRefs: Set<AttrRef>
) : Source(relativePath) {

  /** The parent of a style resource, e.g. "Theme.AppCompat.Light.DarkActionBar". */
  data class StyleParentRef(val styleParent: String)

  /** * Any attribute that looks like a reference to another resource. */
  data class AttrRef(val type: String, val id: String) {
    companion object {

      private val TYPE_REGEX = Regex("""@(?:.+:)?(.+)/""")
      private val ATTR_REGEX = Regex("""\?(?:.+/)?(.+)""")

      /**
       * On consumer side, only get attrs from the XML document when:
       * 1. They're not an ID (don't start with `@+id` or `@id`)
       * 2. They're not a tools namespace (don't start with `tools:`)
       * 3. Their value starts with `?`, like `?themeColor`.
       * 4. Their value starts with `@`, like `@drawable/`.
       *
       * Will return `null` if the map entry doesn't match an expected pattern.
       */
      fun from(mapEntry: Map.Entry<String, String>): AttrRef? {
        if (mapEntry.isId()) return null
        if (mapEntry.isToolsAttr()) return null

        val id = mapEntry.value
        return if (id.startsWith('?')) {
          AttrRef(
            type = "attr",
            id = id.attr().replace('.', '_')
          )
        } else if (id.startsWith("@")) {
          AttrRef(
            type = id.type(),
            // @drawable/some_drawable => some_drawable
            id = id.substringAfterLast('/').replace('.', '_')
          )
        } else {
          null
        }
      }

      private fun Map.Entry<String, String>.isId() = value.startsWith("@+") || value.startsWith("@id")
      private fun Map.Entry<String, String>.isToolsAttr() = key.startsWith("tools:")

      // @drawable/some_drawable => drawable
      // @android:drawable/some_drawable => drawable
      private fun String.type(): String = TYPE_REGEX.find(this)!!.groupValues[1]

      // ?themeColor => themeColor
      // ?attr/themeColor => themeColor
      private fun String.attr(): String = ATTR_REGEX.find(this)!!.groupValues[1]
    }
  }
}