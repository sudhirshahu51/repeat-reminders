package com.sudhirshahu.loopalarm.data

/**
 * Reminder notes as a list of points, like a OneNote list: bullets, numbers or a checklist.
 * Stored in [Alarm.notes] one point per line with a marker ("- ", "1. ", "[ ] " / "[x] "), so older notes,
 * plain lines without a marker, read as bullets.
 */
object NotePoints {
    enum class Style(val label: String) { BULLETS("Bullets"), NUMBERS("Numbers"), CHECKLIST("Checklist") }

    data class Point(val text: String, val checked: Boolean = false)

    data class Notes(val style: Style, val points: List<Point>) {
        val isEmpty: Boolean get() = points.none { it.text.isNotBlank() }
    }

    private val numbered = Regex("""^\d{1,3}[.)]\s+""")
    private val checkbox = Regex("""^\[( |x|X)]\s*""")
    private val bullet = Regex("""^[-*•]\s+""")

    fun parse(notes: String): Notes {
        val lines = notes.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return Notes(Style.BULLETS, emptyList())
        // The list style is the first line's; each line still loses whatever marker it has.
        val style = when {
            checkbox.containsMatchIn(lines[0]) -> Style.CHECKLIST
            numbered.containsMatchIn(lines[0]) -> Style.NUMBERS
            else -> Style.BULLETS
        }
        val points = lines.map { line ->
            val check = checkbox.find(line)
            when {
                check != null -> Point(line.substring(check.range.last + 1).trim(), checked = check.groupValues[1].equals("x", true))
                numbered.containsMatchIn(line) -> Point(line.replaceFirst(numbered, "").trim())
                bullet.containsMatchIn(line) -> Point(line.replaceFirst(bullet, "").trim())
                else -> Point(line)
            }
        }
        return Notes(style, points)
    }

    /** Back to the stored text; empty points are dropped. */
    fun format(notes: Notes): String = notes.points.filter { it.text.isNotBlank() }.mapIndexed { i, p ->
        val text = p.text.trim().replace('\n', ' ')
        when (notes.style) {
            Style.BULLETS -> "- $text"
            Style.NUMBERS -> "${i + 1}. $text"
            Style.CHECKLIST -> "[${if (p.checked) "x" else " "}] $text"
        }
    }.joinToString("\n")

    /** The marker shown before point [index]: •, 1., ☐ or ☑. */
    fun marker(style: Style, index: Int, checked: Boolean): String = when (style) {
        Style.BULLETS -> "•"
        Style.NUMBERS -> "${index + 1}."
        Style.CHECKLIST -> if (checked) "☑" else "☐"
    }

    /** Lines with their markers, for notifications and other plain text. */
    fun displayLines(notes: String): List<String> {
        val n = parse(notes)
        return n.points.filter { it.text.isNotBlank() }.mapIndexed { i, p -> "${marker(n.style, i, p.checked)} ${p.text}" }
    }
}
