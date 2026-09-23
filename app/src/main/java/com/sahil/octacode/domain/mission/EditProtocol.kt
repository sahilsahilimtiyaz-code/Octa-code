package com.sahil.octacode.domain.mission

/**
 * M3c edit protocol: models emit full file replacements only (Option A).
 *
 * ```
 * ===OCTA_EDIT===
 * path: relative/path/from/project/root
 * ===CONTENT===
 * …full new file contents…
 * ===END===
 * ```
 * Repeat per file. No markdown fences. No partial hunks.
 */
object EditProtocol {

    data class FileEdit(val path: String, val content: String)

    private const val BEGIN = "===OCTA_EDIT==="
    private const val PATH_PREFIX = "path:"
    private const val CONTENT = "===CONTENT==="
    private const val END = "===END==="

    fun systemPrompt(): String = """
You are Octa Code, a coding agent implementing a mission with FULL FILE REPLACEMENTS.

Rules:
1. Output ONLY edit blocks in this exact format (no markdown fences, no prose):
$BEGIN
path: relative/path/from/project/root
$CONTENT
...complete new contents of that file...
$END
2. Each block is one file. Content is the ENTIRE file, not a diff.
3. Paths are relative to the project root. No absolute paths, no "..".
4. Modify only files needed for the goal. Prefer creating new files over huge rewrites when reasonable.
5. After the last block, stop. Do not explain.
""".trim()

    fun parse(modelOutput: String): List<FileEdit> {
        val lines = modelOutput.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val edits = mutableListOf<FileEdit>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].trim() != BEGIN) {
                i++
                continue
            }
            i++
            var path: String? = null
            while (i < lines.size && lines[i].trim() != CONTENT) {
                val line = lines[i].trim()
                if (line.startsWith(PATH_PREFIX)) {
                    path = line.removePrefix(PATH_PREFIX).trim()
                }
                i++
            }
            if (i >= lines.size || lines[i].trim() != CONTENT) break
            i++ // past CONTENT
            val contentLines = mutableListOf<String>()
            var closed = false
            while (i < lines.size) {
                if (lines[i].trim() == END) {
                    closed = true
                    i++
                    break
                }
                contentLines += lines[i]
                i++
            }
            if (closed && !path.isNullOrBlank()) {
                // Drop a single trailing empty line from the split artifact if present
                // only when content itself would end with newline anyway — keep raw join.
                edits += FileEdit(path = path, content = contentLines.joinToString("\n"))
            }
        }
        return edits
    }
}
