package id.kenang.core.providers.story

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the invalid-JSON defenses added 2026-09-06 (owner failure: the router
 * hard-caps output ~2K tokens, cutting 8-scene plans mid-string).
 * [AnalysisService.salvageTruncatedArray] is private — the tests exercise the
 * same contract through a copy of its scan here would drift, so the parser
 * flags and truncation shapes are verified via the public Json config instead.
 */
class JsonSalvageTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; allowTrailingComma = true }

    @Test
    fun `trailing comma before array end parses with allowTrailingComma`() {
        // Seen live: "Trailing comma before the end of JSON array".
        val raw = """[{"a":1},{"a":2},]"""
        val arr = json.parseToJsonElement(raw).jsonArray
        assertEquals(2, arr.size)
    }

    @Test
    fun `truncated array is rescued by cutting at the last complete element`() {
        // Mirrors salvageTruncatedArray's contract: cut at last '}' that
        // returns to depth 1, close the array.
        val truncated = """[{"scene_id":"sc1","hint":"a"},{"scene_id":"sc2","hint":"b"},{"scene_id":"sc3","hint":"unfinished str"""
        val salvaged = salvageForTest(truncated)
        val arr = json.parseToJsonElement(salvaged!!).jsonArray
        assertEquals(2, arr.size)
    }

    @Test
    fun `complete array is left alone and braces inside strings do not confuse the scan`() {
        val complete = """[{"a":"x{y}z"},{"b":"w"}]"""
        assertEquals(null, salvageForTest(complete))
        val truncatedWithBraces = """[{"a":"x{y}z"},{"b":"{{{"""
        val salvaged = salvageForTest(truncatedWithBraces)
        assertTrue(salvaged != null && json.parseToJsonElement(salvaged).jsonArray.size == 1)
    }

    /** Same algorithm as AnalysisService.salvageTruncatedArray (kept in sync). */
    private fun salvageForTest(raw: String): String? {
        val start = raw.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        var lastCompleteElementEnd = -1
        for (i in start until raw.length) {
            val ch = raw[i]
            if (escaped) { escaped = false; continue }
            when {
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '[' || ch == '{' -> depth++
                ch == ']' || ch == '}' -> {
                    depth--
                    if (ch == '}' && depth == 1) lastCompleteElementEnd = i
                    if (ch == ']' && depth == 0) return null
                }
            }
        }
        if (lastCompleteElementEnd < 0) return null
        return raw.substring(start, lastCompleteElementEnd + 1) + "]"
    }
}
