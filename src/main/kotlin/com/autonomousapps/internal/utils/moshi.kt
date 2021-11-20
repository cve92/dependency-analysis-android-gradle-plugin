@file:JvmName("MoshiUtils")

package com.autonomousapps.internal.utils

import com.autonomousapps.model.Coordinates
import com.autonomousapps.model.FlatCoordinates
import com.autonomousapps.model.ModuleCoordinates
import com.autonomousapps.model.ProjectCoordinates
import com.squareup.moshi.*
import com.squareup.moshi.Types.newParameterizedType
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

val MOSHI: Moshi by lazy {
  Moshi.Builder()
    .add(DependencyGraphAdapter())
    .add(CoordinatesAdapter())
    .add(KotlinJsonAdapterFactory())
    .add(TypeAdapters())
    .build()
}

inline fun <reified T> getJsonAdapter(withNulls: Boolean = false): JsonAdapter<T> {
  val adapter = MOSHI.adapter(T::class.java)
  return if (withNulls) {
    adapter.serializeNulls()
  } else {
    adapter
  }
}

inline fun <reified T> getJsonListAdapter(withNulls: Boolean = false): JsonAdapter<List<T>> {
  val type = newParameterizedType(List::class.java, T::class.java)
  val adapter = MOSHI.adapter<List<T>>(type)
  return if (withNulls) {
    adapter.serializeNulls()
  } else {
    adapter
  }
}

inline fun <reified T> getJsonSetAdapter(withNulls: Boolean = false): JsonAdapter<Set<T>> {
  val type = newParameterizedType(Set::class.java, T::class.java)
  val adapter = MOSHI.adapter<Set<T>>(type)
  return if (withNulls) {
    adapter.serializeNulls()
  } else {
    adapter
  }
}

inline fun <reified K, reified V> getJsonMapAdapter(withNulls: Boolean = false): JsonAdapter<Map<K, V>> {
  val type = newParameterizedType(Map::class.java, K::class.java, V::class.java)
  val adapter = MOSHI.adapter<Map<K, V>>(type)
  return if (withNulls) {
    adapter.serializeNulls()
  } else {
    adapter
  }
}

inline fun <reified T> String.fromJson(): T {
  return getJsonAdapter<T>().fromJson(this)!!
}

inline fun <reified T> T.toJson(withNulls: Boolean = false): String {
  return getJsonAdapter<T>(withNulls).toJson(this)
}

inline fun <reified T> String.fromJsonList(withNulls: Boolean = false): List<T> {
  return getJsonListAdapter<T>(withNulls).fromJson(this)!!
}

inline fun <reified T> String.fromJsonSet(withNulls: Boolean = false): Set<T> {
  return getJsonSetAdapter<T>(withNulls).fromJson(this)!!
}

inline fun <reified K, reified V> String.fromJsonMap(): Map<K, V> {
  val mapType = newParameterizedType(Map::class.java, K::class.java, V::class.java)
  val adapter = MOSHI.adapter<Map<K, V>>(mapType)
  return adapter.fromJson(this)!!
}

inline fun <reified K, reified V> String.fromJsonMapList(): Map<K, List<V>> {
  val listType = newParameterizedType(List::class.java, V::class.java)
  val mapType = newParameterizedType(Map::class.java, K::class.java, listType)
  val adapter = MOSHI.adapter<Map<K, List<V>>>(mapType)

  return adapter.fromJson(this)!!
}

inline fun <reified T> T.toPrettyString(withNulls: Boolean = false): String {
  return getJsonAdapter<T>(withNulls).indent("  ").toJson(this)
}

inline fun <reified K, reified V> Map<K, V>.toPrettyString(withNulls: Boolean = false): String {
  return getJsonMapAdapter<K, V>(withNulls).indent("  ").toJson(this)
}

@Suppress("unused")
internal class TypeAdapters {

  @ToJson fun fileToJson(file: File): String = file.absolutePath
  @FromJson fun fileFromJson(absolutePath: String): File = File(absolutePath)
}

@Suppress("unused")
internal class CoordinatesAdapter {

  @ToJson fun fromCoordinates(
    writer: JsonWriter,
    coordinates: Coordinates,
    projectDelegate: JsonAdapter<ProjectCoordinates>,
    moduleDelegate: JsonAdapter<ModuleCoordinates>,
    flatDelegate: JsonAdapter<FlatCoordinates>
  ): Unit = when (coordinates) {
    is ProjectCoordinates -> projectDelegate.toJson(writer, coordinates)
    is ModuleCoordinates -> moduleDelegate.toJson(writer, coordinates)
    is FlatCoordinates -> flatDelegate.toJson(writer, coordinates)
  }

  @FromJson fun toCoordinates(
    reader: JsonReader,
    projectDelegate: JsonAdapter<ProjectCoordinates>,
    moduleDelegate: JsonAdapter<ModuleCoordinates>,
    flatDelegate: JsonAdapter<FlatCoordinates>
  ): Coordinates =
    tryFromModule(reader, moduleDelegate)
      ?: tryFromProject(reader, projectDelegate)
      ?: tryFromFlat(reader, flatDelegate)
      ?: error("FromJson toCoordinates shouldn't be able to fail.")

  private fun tryFromModule(
    reader: JsonReader,
    moduleDelegate: JsonAdapter<ModuleCoordinates>
  ): ModuleCoordinates? = try {
    moduleDelegate.fromJson(reader)
  } catch (_: Exception) {
    null
  }

  private fun tryFromProject(
    reader: JsonReader,
    projectDelegate: JsonAdapter<ProjectCoordinates>
  ): ProjectCoordinates? = try {
    projectDelegate.fromJson(reader)
  } catch (_: Exception) {
    null
  }

  private fun tryFromFlat(
    reader: JsonReader,
    flatDelegate: JsonAdapter<FlatCoordinates>
  ): FlatCoordinates? = try {
    flatDelegate.fromJson(reader)
  } catch (_: Exception) {
    null
  }
}
