package me.anno.graph.types.flow.actions

import me.anno.graph.NodeOutput
import me.anno.graph.types.FlowGraph
import me.anno.graph.types.flow.control.FixedControlFlowNode

abstract class ActionNode : FixedControlFlowNode {

    constructor(name: String) : super(name)

    constructor(name: String, inputs: List<String>, outputs: List<String>) :
            super(name, Companion.inputs + inputs, Companion.outputs + outputs)

    abstract fun executeAction(graph: FlowGraph)

    override fun execute(graph: FlowGraph): NodeOutput {
        executeAction(graph)
        return getOutputNodes(0)
    }

    companion object {
        val inputs = listOf("Flow", beforeName)
        val outputs = listOf("Flow", afterName)
    }

}