package com.autonomousapps.model

import java.nio.file.Path

/**
 * TODO.
 */
sealed class Source(
  val name: String,
  val file: Path
)

/**
 * TODO.
 */
class CodeSource(name: String, file: Path) : Source(name, file)

/**
 * TODO.
 */
class AndroidResSource(name: String, file: Path) : Source(name, file)
