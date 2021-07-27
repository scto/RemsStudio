package me.anno.mesh.vox.format

import me.anno.ecs.Entity
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.components.mesh.MeshComponent
import me.anno.ecs.components.mesh.MeshRenderer
import me.anno.ecs.prefab.*
import org.joml.Vector3d

class Node {

    // meta data
    var name: String = ""

    // hierarchy
    var child: Node? = null
    var children: List<Node>? = null
    var models: IntArray? = null

    // transform
    var px = 0.0
    var py = 0.0
    var pz = 0.0
    var ry = 0.0

    fun containsModel(): Boolean {
        return if (models ?: child?.models != null) true
        else children?.any { it.containsModel() } ?: false
    }

    fun toEntity(meshes: List<Mesh>): Entity {
        val entity = Entity(name)
        val models = models ?: child?.models
        if (models != null) {
            // add these models as components
            for (modelIndex in models) {
                val mesh = meshes[modelIndex]
                entity.add(MeshComponent(mesh))
            }
            entity.add(MeshRenderer())
        }
        if (px != 0.0 || py != 0.0 || pz != 0.0) {
            entity.transform.localPosition = Vector3d(px, py, pz)
            // rotation is found, but looks wrong in the price of persia sample
        }
        val children = children
        if (children != null) {
            for (child in children) {
                entity.add(child.toEntity(meshes))
            }
        }
        return entity
    }

    fun toEntityPrefab(changes: MutableList<Change>, meshes: List<Mesh>, parentPath: IntArray, path: IntArray) {
        changes.add(ChangeAddEntity(Path(parentPath)))
        if (name != "") changes.add(ChangeSetEntityAttribute(Path(path, "name"), name))
        val models = models ?: child?.models
        if (models != null) {
            // add these models as components
            for ((compIndex, modelIndex) in models.withIndex()) {
                val mesh = meshes[modelIndex]
                changes.add(ChangeAddComponent(Path(path), "MeshComponent"))
                val compPath = path + compIndex
                changes.add(ChangeSetComponentAttribute(Path(compPath, "mesh"), mesh))
            }
            changes.add(ChangeAddComponent(Path(path), "MeshRenderer"))
        }
        if (px != 0.0 || py != 0.0 || pz != 0.0) {
            changes.add(ChangeSetEntityAttribute(Path(path, "position"), Vector3d(px, py, pz)))
            // rotation is found, but looks wrong in the price of persia sample
        }
        children?.forEachIndexed { childIndex, childNode ->
            childNode.toEntityPrefab(changes, meshes, path + childIndex, path)
        }
    }

}