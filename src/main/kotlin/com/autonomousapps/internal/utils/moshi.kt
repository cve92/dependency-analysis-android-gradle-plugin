@file:JvmName("MoshiUtils")

package com.autonomousapps.internal.utils

import com.autonomousapps.model.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types.newParameterizedType
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

val MOSHI: Moshi by lazy {
  Moshi.Builder()
    .add(DependencyGraphAdapter())
    .add(
      PolymorphicJsonAdapterFactory.of(Coordinates::class.java, "coord")
        .withSubtype(ModuleCoordinates::class.java, "module_coord")
        .withSubtype(ProjectCoordinates::class.java, "project_coord")
        .withSubtype(FlatCoordinates::class.java, "flat_coord")
    )
    .add(
      PolymorphicJsonAdapterFactory.of(Dependency::class.java, "dep")
        .withSubtype(ModuleDependency::class.java, "module_dep")
        .withSubtype(ProjectDependency::class.java, "project_dep")
        .withSubtype(FlatDependency::class.java, "flat_dep")
    )
    .add(
      PolymorphicJsonAdapterFactory.of(Capability::class.java, "cap")
        .withSubtype(AndroidLinterCapability::class.java, "lint_cap")
        .withSubtype(AndroidManifestCapability::class.java, "man_cap")
        .withSubtype(AndroidResCapability::class.java, "res_cap")
        .withSubtype(AnnotationProcessorCapability::class.java, "proc_cap")
        .withSubtype(ClassCapability::class.java, "class_cap")
        .withSubtype(ConstantCapability::class.java, "const_cap")
        .withSubtype(InferredCapability::class.java, "infer_cap")
        .withSubtype(InlineMemberCapability::class.java, "inline_cap")
        .withSubtype(KtFileCapability::class.java, "kt_cap")
        .withSubtype(NativeLibCapability::class.java, "nat_cap")
        .withSubtype(ServiceLoaderCapability::class.java, "service_cap")
        .withSubtype(SecurityProviderCapability::class.java, "prov_cap")
    )
    .add(TypeAdapters())
    .addLast(KotlinJsonAdapterFactory())
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
