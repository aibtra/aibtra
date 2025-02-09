package dev.aibtra.ai

interface AIReasoningFilter {
	fun process(raw: StringBuilder, filtered: StringBuilder, finished: Boolean): Boolean

	class NoFilter : AIReasoningFilter {
		private var lastRawLength = 0
		private var lastFilteredLength = 0

		override fun process(raw: StringBuilder, filtered: StringBuilder, finished: Boolean): Boolean {
			require(lastRawLength <= raw.length)
			require(lastFilteredLength <= filtered.length)

			val lastRawLength = this.lastRawLength
			this.lastRawLength = raw.length
			this.lastFilteredLength = filtered.length

			filtered.append(raw.substring(lastRawLength))
			return true
		}

		companion object {
			private const val THINK_START = "<think>\n"
			private const val THINK_END = "\n</think>\n"
		}
	}
}