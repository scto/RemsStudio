package me.anno.remsstudio.ui.graphs

import me.anno.animation.Interpolation
import me.anno.input.MouseButton
import me.anno.remsstudio.Selection.selectedProperty
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelList
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelX
import me.anno.ui.style.Style

class GraphEditor(style: Style) : PanelListY(style) {

    val controls = ScrollPanelX(style)
    val body = GraphEditorBody(style)

    init {
        this += controls
        body.weight = 1f
        this += body
        val cc = controls.child as PanelList
        for (type in Interpolation.values()) {
            cc += object : TextButton(type.symbol, true, style) {
                override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
                    println("textButton.onMouseDown")
                }
                override fun onUpdate() {
                    isVisible = body.selectedKeyframes.isNotEmpty()
                    super.onUpdate()
                }
            }
                .apply {
                    padding.left = 2
                    padding.right = 2
                    padding.top = 0
                    padding.bottom = 0
                }
                .setTooltip(if (type.description.isEmpty()) type.displayName else "${type.displayName}: ${type.description}")
                .addLeftClickListener {
                    println("setting $type to ${body.selectedKeyframes}")
                    for(it in body.selectedKeyframes) it.interpolation = type
                    body.invalidateDrawing()
                }
        }

    }

    override fun onUpdate() {
        super.onUpdate()

        // explicitly even for invisible children
        controls.forAllPanels {
            it.onUpdate()
            it.tick()
        }

        children[0].isVisible = selectedProperty?.isAnimated == true
    }

    override val className get() = "GraphEditor"

}