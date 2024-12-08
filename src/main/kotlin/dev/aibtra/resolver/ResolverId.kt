package dev.aibtra.resolver

/**
 * A straightforward wrapper to enhance type safety for the core data structures.
 * The goal is to maintain a consistent set of related core data structures with identical ids.
 */
data class ResolverId(private val id: String) {
	override fun toString(): String {
		return id
	}
}