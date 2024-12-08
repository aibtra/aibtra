package dev.aibtra.core

fun <T> List<T>.areAllUnique(): Boolean = this.size == this.toSet().size

fun <T> List<T>.isSortedStrictly(comparator: Comparator<T>) : Boolean {
	return zipWithNext { a, b -> comparator.compare(a, b) < 0 }.all { it }
}