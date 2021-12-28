package me.anno.utils.structures.lists

import kotlin.math.min

class LimitedList<V>(limit: Int = 16) : MutableCollection<V> {

    private val data = arrayOfNulls<Any>(limit)

    fun isFull() = size >= data.size

    override var size = 0

    override fun clear() {
        size = 0
        data.fill(null)
    }

    override fun add(element: V): Boolean {
        if (element in this) return false
        if (size < data.size) data[size] = element
        size++
        return size <= data.size
    }

    override fun remove(element: V): Boolean {
        var writeIndex = 0
        val oldSize = size
        for (i in 0 until oldSize) {
            val e = data[i]
            if (e != element) {
                data[writeIndex++] = e
            } else size--
        }
        return writeIndex < oldSize || oldSize > data.size
    }

    override fun removeAll(elements: Collection<V>): Boolean {
        for (e in elements) remove(e)
        return true
    }

    override fun retainAll(elements: Collection<V>): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(elements: Collection<V>): Boolean {
        val targetSize = data.size + elements.size
        for (e in elements) {
            if (!add(e)) {
                size = targetSize
                return false
            }
        }
        return size <= data.size
    }

    override fun isEmpty(): Boolean = size == 0

    override operator fun contains(element: V): Boolean {
        if (size > data.size) return true
        for (i in 0 until min(size, data.size)) {
            if (data[i] == element) return true
        }
        return false
    }

    override fun containsAll(elements: Collection<V>): Boolean {
        return if (size > data.size) true
        else elements.all { it in this }
    }

    override fun iterator(): MutableIterator<V> {
        return object : MutableIterator<V> {
            private var i = 0
            override fun hasNext() = i < size && i < data.size
            override fun next(): V = data[i++] as V
            override fun remove() {
                remove(data[i - 1])
            }
        }
    }

    override fun toString(): String {
        return Array(size) { data[it] }.joinToString()
    }

}