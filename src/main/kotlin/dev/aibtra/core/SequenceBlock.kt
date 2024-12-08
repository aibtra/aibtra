package dev.aibtra.core

data class SequenceBlock(
	val leftFrom: Int,
	val leftTo: Int,
	val rightFrom: Int, // exclusive!
	val rightTo: Int // exclusive!
) {
	val leftSize
		get() = leftTo - leftFrom
	val rightSize
		get() = rightTo - rightFrom

	operator fun get(side: Side, mode: Mode): Int {
		return when (side) {
			Side.LEFT -> when (mode) {
				Mode.FROM -> leftFrom
				Mode.TO -> leftTo
			}
			Side.RIGHT -> when (mode) {
				Mode.FROM -> rightFrom
				Mode.TO -> rightTo
			}
		}
	}

	enum class Side {
		LEFT, RIGHT;

		fun other(): Side {
			return when (this) {
				LEFT -> RIGHT
				RIGHT -> LEFT
			}
		}
	}

	enum class Mode {
		FROM, TO
	}
}