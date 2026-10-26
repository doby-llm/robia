package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path

/**
 * DARK1 scan (R3-DEC-045): a colour literal in a UI file must say why it stays the same in both themes.
 * Put `// theme-fixed: <reason>` on the line, or one `// theme-fixed-file: <reason>` line in a file that is colour data.
 */
internal object ThemeSourceScan {
    private val literal = Regex("""\bColor\(0x|\bColor\.White\b|\bColor\.Black\b""")
    private val lineMarker = Regex("""//\s*theme-fixed:\s*\S""")
    private val fileMarker = Regex("""//\s*theme-fixed-file:\s*\S""")

    /** 1-based line numbers and trimmed text of every literal without a line marker; empty for a file marker. */
    fun unmarkedLiterals(text: String): List<Pair<Int, String>> {
        if (text.lines().any(fileMarker::containsMatchIn)) return emptyList()
        return text.lines().mapIndexedNotNull { index, line ->
            if (literal.containsMatchIn(line) && !lineMarker.containsMatchIn(line)) index + 1 to line.trim() else null
        }
    }

    /** "path:line: text" for every unmarked literal in the repo-relative [relativePaths]. */
    fun scan(relativePaths: List<String>): List<String> = relativePaths.flatMap { path ->
        unmarkedLiterals(source(path)).map { (line, text) -> "$path:$line: $text" }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
