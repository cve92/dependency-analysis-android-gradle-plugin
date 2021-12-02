package com.autonomousapps.internal.configuration

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ConfigurationsTest {

  @DisplayName("Configurations.isVariant()")
  @ParameterizedTest(name = "isVariant({0}) == {1}")
  @CsvSource(
    value = [
      "debugApi, true",
      "releaseImplementation, true",
      "kaptDebug, true",
      "kapt, false",
      "implementation, false",
      "api, false"
    ]
  )
  fun `is variant`(candidate: String, value: Boolean) {
    assertThat(Configurations.isVariant(candidate)).isEqualTo(value)
  }
}
