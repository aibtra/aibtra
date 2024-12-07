package dev.aibtra.diff

import de.regnis.q.sequence.core.QSequenceMedia
import dev.aibtra.core.*

sealed interface DiffTokenizing {
	fun createMedia(): QSequenceMedia

	fun getRawLength(): Int

	fun getRefLength(): Int

	fun equals(raw: Int, ref: Int): Boolean

	fun equalsRaw(p0: Int, p1: Int): Boolean

	fun equalsRef(p0: Int, p1: Int): Boolean

	fun isRefAtEndOfWord(pos: Int): Boolean

	fun isRefAtStartOfWord(pos: Int): Boolean

	fun isRefWhitespace(pos: Int): Boolean

	fun toCharBlock(block: DiffBlock): DiffBlock

	companion object {
		fun create(raw: String, ref: String, mode: DiffTokenizingMode): DiffTokenizing {
			return when (mode) {
				DiffTokenizingMode.NONE -> CharacterMedia(raw, ref)
				DiffTokenizingMode.ALPHANUMERIC -> AlphanumericMedia(AlphaNumericTokenizing.tokenize(raw), AlphaNumericTokenizing.tokenize(ref))
			}
		}
	}

	class CharacterMedia(private val raw: String, private val ref: String) : DiffTokenizing {
		override fun createMedia(): QSequenceMedia {
			return object : QSequenceMedia {
				override fun equals(p0: Int, p1: Int): Boolean = raw[p0] == ref[p1]
				override fun getLeftLength(): Int = raw.length
				override fun getRightLength(): Int = ref.length
			}
		}

		override fun getRawLength(): Int = raw.length
		override fun getRefLength(): Int = ref.length
		override fun equals(raw: Int, ref: Int): Boolean = this.raw[raw] == this.ref[ref]
		override fun equalsRaw(p0: Int, p1: Int): Boolean = raw[p0] == raw[p1]
		override fun equalsRef(p0: Int, p1: Int): Boolean = ref[p0] == ref[p1]
		override fun isRefWhitespace(pos: Int): Boolean = ref[pos].isWhitespace()
		override fun toCharBlock(block: DiffBlock): DiffBlock = block

		override fun isRefAtEndOfWord(pos: Int): Boolean {
			if (pos < 0) {
				return false
			}
			if (pos == this.ref.length || pos < this.ref.length && this.ref[pos].isWhitespace()) {
				return !this.ref[pos - 1].isWhitespace()
			}
			return false
		}

		override fun isRefAtStartOfWord(pos: Int): Boolean {
			if (pos == ref.length) {
				return false
			}
			if (pos == 0 || pos > 0 && ref[pos - 1].isWhitespace()) {
				return !ref[pos].isWhitespace()
			}
			return false
		}
	}

	class AlphanumericMedia(private val raw: AlphaNumericTokenizing, private val ref: AlphaNumericTokenizing) : DiffTokenizing {
		override fun createMedia(): QSequenceMedia {
			return object : QSequenceMedia {
				override fun equals(p0: Int, p1: Int): Boolean = raw[p0] == ref[p1]
				override fun getLeftLength(): Int = raw.length
				override fun getRightLength(): Int = ref.length
			}
		}

		override fun getRawLength(): Int = raw.length
		override fun getRefLength(): Int = ref.length
		override fun equals(raw: Int, ref: Int): Boolean = this.raw[raw] == this.ref[ref]
		override fun equalsRaw(p0: Int, p1: Int): Boolean = raw[p0] == raw[p1]
		override fun equalsRef(p0: Int, p1: Int): Boolean = ref[p0] == ref[p1]
		override fun isRefAtEndOfWord(pos: Int): Boolean = false
		override fun isRefAtStartOfWord(pos: Int): Boolean = false
		override fun isRefWhitespace(pos: Int): Boolean = ref[pos].let { it == "" }

		override fun toCharBlock(block: DiffBlock): DiffBlock {
			return DiffBlock(
				raw.charPos(block.rawFrom),
				raw.charPos(block.rawTo),
				ref.charPos(block.refFrom),
				ref.charPos(block.refTo)
			)
		}
	}
}