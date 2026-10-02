package ai.sonora.mobile.domain

/**
 * Group volume without destroying the balance between rooms. The hub's group-volume endpoint sets
 * every member to the same absolute value, so it is never called; instead each member is scaled by
 * new / loudest. A single room is just a group of one.
 */
object GroupVolume {
    /**
     * [base] is each member's volume when the drag started and [newValue] the pill's value
     * (clamped to 0..100). Each member becomes `floor(v * new / top + 0.5)` clamped to 0..100 with
     * `top = max(base)`; if every member is at 0 they all get [newValue]. Integer arithmetic only,
     * so every platform rounds the same way.
     */
    fun scale(base: Map<String, Int>, newValue: Int): Map<String, Int> {
        if (base.isEmpty()) return emptyMap()
        val target = newValue.coerceIn(0, 100)
        val top = base.values.max()
        if (top <= 0) return base.mapValues { target }
        return base.mapValues { (_, v) -> ((2 * v * target + top) / (2 * top)).coerceIn(0, 100) }
    }
}
