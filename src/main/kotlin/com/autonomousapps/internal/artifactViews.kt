package com.autonomousapps.internal

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.attributes.Attribute

private val attributeKey: Attribute<String> = Attribute.of("artifactType", String::class.java)

internal fun ResolvableDependencies.artifactViewFor(attrValue: String): ArtifactView = artifactView {
  attributes.attribute(attributeKey, attrValue)
  lenient(true)
}

internal fun Configuration.artifactsFor(attrValue: String): ArtifactCollection = incoming
  .artifactViewFor(attrValue)
  .artifacts

internal object ArtifactAttributes {
  /** Deprecated. Replaced with [ANDROID_CLASSES_JAR] in AGP 7+. Used only in AGP 4. */
  const val ANDROID_CLASSES_JAR_4 = "android-classes-jar"
  const val ANDROID_CLASSES_JAR = "android-classes"
  const val ANDROID_JNI = "android-jni"
  const val ANDROID_LINT = "android-lint"
}
