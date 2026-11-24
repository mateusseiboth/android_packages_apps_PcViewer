package org.lineageos.pcviewer.core

sealed interface NameProblem {
    data object Empty : NameProblem

    data object StartsWithDot : NameProblem

    data class InvalidCharacter(val character: Char) : NameProblem
}

/** Checks for a new file name; characters that some file systems refuse are refused everywhere. */
object FileNames {
    private const val INVALID = "/\\:*?\"<>|"

    private val RULES: List<(String) -> NameProblem?> = listOf(
        { name -> NameProblem.Empty.takeIf { name.isBlank() } },
        { name -> NameProblem.StartsWithDot.takeIf { name.startsWith(".") } },
        { name -> name.firstOrNull { it in INVALID || it.isISOControl() }?.let { NameProblem.InvalidCharacter(it) } },
    )

    fun problemWith(name: String): NameProblem? = RULES.firstNotNullOfOrNull { it(name) }

    /** How much of the name to select for editing: everything before the extension. */
    fun baseLength(name: String): Int = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
}
