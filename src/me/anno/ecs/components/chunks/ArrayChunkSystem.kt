package me.anno.ecs.components.chunks

/**
 * a typical implementation for a chunk system would be an array backend
 * */
abstract class ArrayChunkSystem<Element>(
    bitsX: Int, bitsY: Int, bitsZ: Int,
    val defaultElement: Element
) : ChunkSystem<Array<Element>, Element>(bitsX, bitsY, bitsZ) {

    abstract fun generateChunk(chunkX: Int, chunkY: Int, chunkZ: Int, chunk: Array<Element>)

    override fun createChunk(chunkX: Int, chunkY: Int, chunkZ: Int, size: Int): Array<Element> {
        val data = if (defaultElement == null) {
            arrayOfNulls<Any?>(size) as Array<Element>
        } else {
            Array<Any?>(size) { defaultElement } as Array<Element>
        }
        generateChunk(chunkX, chunkY, chunkZ, data)
        return data
    }

    override fun getElement(
        container: Array<Element>,
        localX: Int, localY: Int, localZ: Int,
        yzxIndex: Int
    ): Element {
        return container[yzxIndex]
    }

    override fun setElement(
        container: Array<Element>,
        localX: Int, localY: Int, localZ: Int,
        yzxIndex: Int,
        element: Element
    ): Boolean {
        container[yzxIndex] = element
        return true
    }

}