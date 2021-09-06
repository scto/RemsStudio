package me.anno.ecs.prefab

import me.anno.ecs.prefab.PrefabCache.getPrefabPair
import me.anno.io.ISaveable
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import kotlin.reflect.KClass

open class PrefabByFileCache<V : ISaveable>(val clazz: KClass<V>) {

    operator fun get(ref: FileReference?) = get(ref, false)
    operator fun get(ref: FileReference?, default: V) = get(ref, false) ?: default

    fun getPrefab(ref: FileReference?, async: Boolean): Prefab? {
        if (ref == null || ref == InvalidRef) return null
        return getPrefabPair(ref, async)?.first
    }

    open operator fun get(ref: FileReference?, async: Boolean): V? {
        if (ref == null || ref == InvalidRef) return null
        val pair = getPrefabPair(ref, async)
        val instance = pair?.second ?: return null
        return if (clazz.isInstance(instance)) instance as V else null
    }

}