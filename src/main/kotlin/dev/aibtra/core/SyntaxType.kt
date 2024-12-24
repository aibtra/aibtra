package dev.aibtra.core

import org.fife.ui.rsyntaxtextarea.*
import java.nio.file.*
import kotlin.io.path.*

data class SyntaxType(val internal: String) {
	companion object {
		val NONE: SyntaxType = SyntaxType(SyntaxConstants.SYNTAX_STYLE_NONE)

		fun fromPath(path: Path): SyntaxType {
			val extension = path.extension
			val constant = getSyntaxConstantForExtension(extension)
			if (constant == SyntaxConstants.SYNTAX_STYLE_NONE) {
				return NONE
			}

			return SyntaxType(constant)
		}

		private fun getSyntaxConstantForExtension(extension: String): String {
			return when (extension.lowercase()) {
				"as" -> SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT
				"asm" -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_X86
				"a65" -> SyntaxConstants.SYNTAX_STYLE_ASSEMBLER_6502
				"bbcode" -> SyntaxConstants.SYNTAX_STYLE_BBCODE
				"c" -> SyntaxConstants.SYNTAX_STYLE_C
				"clj" -> SyntaxConstants.SYNTAX_STYLE_CLOJURE
				"cpp", "cxx", "cc", "h", "hpp", "hxx" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
				"cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP
				"css" -> SyntaxConstants.SYNTAX_STYLE_CSS
				"csv" -> SyntaxConstants.SYNTAX_STYLE_CSV
				"d" -> SyntaxConstants.SYNTAX_STYLE_D
				"dockerfile" -> SyntaxConstants.SYNTAX_STYLE_DOCKERFILE
				"dart" -> SyntaxConstants.SYNTAX_STYLE_DART
				"pas", "pp" -> SyntaxConstants.SYNTAX_STYLE_DELPHI
				"dtd" -> SyntaxConstants.SYNTAX_STYLE_DTD
				"f", "for", "f90" -> SyntaxConstants.SYNTAX_STYLE_FORTRAN
				"go" -> SyntaxConstants.SYNTAX_STYLE_GO
				"groovy" -> SyntaxConstants.SYNTAX_STYLE_GROOVY
				"hbs" -> SyntaxConstants.SYNTAX_STYLE_HANDLEBARS
				"hosts" -> SyntaxConstants.SYNTAX_STYLE_HOSTS
				"htaccess" -> SyntaxConstants.SYNTAX_STYLE_HTACCESS
				"html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML
				"ini" -> SyntaxConstants.SYNTAX_STYLE_INI
				"java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
				"js" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
				"json" -> SyntaxConstants.SYNTAX_STYLE_JSON
				"jsp" -> SyntaxConstants.SYNTAX_STYLE_JSP
				"kt", "kts" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
				"less" -> SyntaxConstants.SYNTAX_STYLE_LESS
				"lisp", "lsp" -> SyntaxConstants.SYNTAX_STYLE_LISP
				"lua" -> SyntaxConstants.SYNTAX_STYLE_LUA
				"makefile" -> SyntaxConstants.SYNTAX_STYLE_MAKEFILE
				"mxml" -> SyntaxConstants.SYNTAX_STYLE_MXML
				"nsis" -> SyntaxConstants.SYNTAX_STYLE_NSIS
				"perl", "pl", "pm" -> SyntaxConstants.SYNTAX_STYLE_PERL
				"php" -> SyntaxConstants.SYNTAX_STYLE_PHP
				"properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
				"py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON
				"rb" -> SyntaxConstants.SYNTAX_STYLE_RUBY
				"sas" -> SyntaxConstants.SYNTAX_STYLE_SAS
				"scala" -> SyntaxConstants.SYNTAX_STYLE_SCALA
				"sql" -> SyntaxConstants.SYNTAX_STYLE_SQL
				"tcl" -> SyntaxConstants.SYNTAX_STYLE_TCL
				"ts" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
				"sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
				"vb" -> SyntaxConstants.SYNTAX_STYLE_VISUAL_BASIC
				"xml" -> SyntaxConstants.SYNTAX_STYLE_XML
				"yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML
				else -> SyntaxConstants.SYNTAX_STYLE_NONE // Default to plain text
			}
		}
	}
}