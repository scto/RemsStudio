package me.anno.ecs.components.physics

import me.anno.io.serialization.SerializedProperty

class Vehicle: Rigidbody() {

    @SerializedProperty
    var suspensionStiffness = 5.88

    @SerializedProperty
    var suspensionCompression = 0.83

    @SerializedProperty
    var suspensionDamping = 0.88

    @SerializedProperty
    var maxSuspensionTravelCm = 500.0

    @SerializedProperty
    var frictionSlip = 10.5

    override val className: String = "Vehicle"

}