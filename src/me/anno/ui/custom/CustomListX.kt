package me.anno.ui.custom

import me.anno.ui.base.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.impl.CustomizingBar
import me.anno.ui.style.Style
import me.anno.utils.clamp
import kotlin.math.max
import kotlin.math.roundToInt

class CustomListX(style: Style): PanelListX(style), CustomList {

    init {
        spacing = style.getSize("custom.drag.size", 2)
    }

    val minSize = 10f

    val weights = HashMap<Panel, Float>()

    fun change(p: Panel, delta: Float){
        weights[p] = clamp((weights[p] ?: 0f) + delta, minSize, w.toFloat())
    }

    override fun move(index: Int, delta: Float) {
        val c1 = children[index-1]
        val c2 = children[index+1]
        change(c1, +delta)
        change(c2, -delta)
        // println(children.filter { it !is CustomizingBar }.joinToString { weights[it].toString() })
    }

    override fun add(child: Panel): PanelList {
        val index = children.size
        if(index > 0){
            super.add(CustomizingBar(index, spacing, 0, style))
        }
        weights[child] = 100f
        return super.add(child)
    }

    fun add(child: Panel, weight: Float): PanelList {
        add(child)
        weights[child] = weight
        return this
    }

    override fun placeInParent(x: Int, y: Int) {
        this.x = x
        this.y = y

        if(children.isEmpty()) return
        if(children.size == 1){

            val child = children.first()
            child.placeInParent(x, y)
            child.w = w
            child.h = h
            child.applyConstraints()

        } else {

            val available = w - (children.size/2) * spacing
            val sumWeight = weights.values.sum()
            val weightScale = w / sumWeight

            var childX = this.x
            for(child in children.filter { it.visibility != Visibility.GONE }){
                val weight = weights[child]
                val childW = if(weight == null)
                    child.minW
                else {
                    val betterWeight = max(weight * weightScale, minSize)
                    if(betterWeight != weight) weights[child] = betterWeight
                    (betterWeight / sumWeight * available).roundToInt()
                }
                child.calculateSize(childW, h)
                child.applyConstraints()
                child.placeInParent(childX, y)
                childX += childW
            }

        }

    }



}