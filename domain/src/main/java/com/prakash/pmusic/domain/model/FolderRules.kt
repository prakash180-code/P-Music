package com.prakash.pmusic.domain.model

/**
 * An immutable snapshot of the enabled folder rules used to decide whether a
 * given file may enter the library.
 *
 * [included] and [excluded] are raw folder paths (any case, with or without a
 * trailing slash); evaluation is case-insensitive and recursive via
 * [FolderRulesMatcher].
 *
 * Rule semantics:
 * - Excluded always wins: a file under any [excluded] folder is never allowed.
 * - When [included] is empty the whole device is scanned (minus excluded).
 * - When [included] is non-empty only files under an included folder are
 *   allowed (still respecting excluded).
 */
data class FolderRules(
    val included: List<String> = emptyList(),
    val excluded: List<String> = emptyList()
) {

    /** True when only explicitly included folders are scanned. */
    val isRestricted: Boolean get() = included.isNotEmpty()

    companion object {
        val EMPTY = FolderRules()
    }
}

/**
 * Pure path-matching rules for the library folder manager.
 *
 * Kept as a plain object so the scanner, the repository and the UI all share
 * one definition of "is this file allowed / inside this folder" and so it can
 * be unit-tested without Android.
 */
object FolderRulesMatcher {

    /** Normalises a path for comparison: trims, flips separators, drops the trailing slash. */
    fun normalize(path: String): String =
        path.trim().replace('\\', '/').trimEnd('/')

    /**
     * True when [path] is [folder] itself or sits anywhere below it
     * (recursive containment), case-insensitively.
     */
    fun isUnder(path: String, folder: String): Boolean {
        val file = normalize(path).lowercase()
        val dir = normalize(folder).lowercase()
        if (file.isEmpty() || dir.isEmpty()) return false
        return file == dir || file.startsWith("$dir/")
    }

    /**
     * Decides whether [filePath] may enter the library given [rules].
     *
     * Excluded always wins; when the rules are unrestricted every other file
     * is allowed; when restricted only files under an included folder pass.
     */
    fun isAllowed(filePath: String, rules: FolderRules): Boolean {
        val file = normalize(filePath).lowercase()
        if (file.isEmpty()) return true
        rules.excluded.forEach { if (isUnder(file, it)) return false }
        if (!rules.isRestricted) return true
        return rules.included.any { isUnder(file, it) }
    }
}
