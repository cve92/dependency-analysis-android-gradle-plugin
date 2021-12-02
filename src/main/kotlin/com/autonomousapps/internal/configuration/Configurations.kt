package com.autonomousapps.internal.configuration

internal object Configurations {
  internal const val CONF_ADVICE_ALL_CONSUMER = "adviceAllConsumer"
  internal const val CONF_ADVICE_ALL_PRODUCER = "adviceAllProducer"

  internal const val CONF_PROJECT_GRAPH_CONSUMER = "projGraphConsumer"
  internal const val CONF_PROJECT_GRAPH_PRODUCER = "projGraphProducer"

  internal const val CONF_PROJECT_METRICS_CONSUMER = "projMetricsConsumer"
  internal const val CONF_PROJECT_METRICS_PRODUCER = "projMetricsProducer"

  private val MAIN_SUFFIXES = listOf("api", "implementation", "compileOnly", "runtimeOnly")
  private val ANNOTATION_PROCESSOR_PREFIXES = listOf("kapt", "annotationProcessor")

  internal fun isMain(configurationName: String): Boolean {
    return MAIN_SUFFIXES.any { suffix -> configurationName.endsWith(suffix = suffix, ignoreCase = true) }
  }

  internal fun isAnnotationProcessor(configurationName: String): Boolean {
    return ANNOTATION_PROCESSOR_PREFIXES.any { prefix -> configurationName.startsWith(prefix) }
  }

  internal fun isVariant(configurationName: String): Boolean {
    val main = MAIN_SUFFIXES.find { configurationName.endsWith(suffix = it, ignoreCase = true) }
    return if (main != null) {
      main != configurationName
    } else {
      ANNOTATION_PROCESSOR_PREFIXES.find { configurationName.startsWith(it) } != configurationName
    }
  }

  internal fun findMain(configurationName: String): String? {
    return MAIN_SUFFIXES.find { configurationName.endsWith(suffix = it, ignoreCase = true) }
      ?: ANNOTATION_PROCESSOR_PREFIXES.find { configurationName.startsWith(it) }
  }
}
