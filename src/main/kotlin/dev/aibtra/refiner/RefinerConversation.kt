package dev.aibtra.refiner

class RefinerConversation(val entries: List<Entry>) {
	fun rollbackTo(entry: Entry): RefinerConversation {
		return RefinerConversation(entries.subList(0, entries.indexOf(entry) + 1))
	}

	fun isEmpty(): Boolean {
		return entries.isEmpty()
	}

	interface Entry {
		val title: String
	}
}