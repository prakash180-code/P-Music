package com.prakash.pmusic.domain.model

/**
 * The rule behind a smart playlist: its contents are derived from this rule
 * rather than from a stored song list, so they stay in sync with the library
 * automatically (favorites, play counts, new scans, ...).
 *
 * The rule is persisted on the playlist as a stable, human-readable string
 * via [encode]; [parse] is the inverse. Unknown or malformed strings parse to
 * null and a playlist with such a rule simply renders as empty instead of
 * crashing. Parameters that come from the library (genre name, artist id +
 * name) are baked into the string so the rule is self-describing and needs no
 * library lookup to render a label.
 */
sealed interface SmartPlaylistRule {

    /** Stable string form stored in the playlists table. */
    val encode: String

    /** Short label used on the list and detail screens. */
    val label: String

    /** Every song marked as a favorite. */
    data object Favorites : SmartPlaylistRule {
        override val encode: String = "favorites"
        override val label: String = "Favorites"
    }

    /** Top played songs, most played first. */
    data object MostPlayed : SmartPlaylistRule {
        override val encode: String = "most_played"
        override val label: String = "Most played"
    }

    /** Newest additions to the library, most recently added first. */
    data object RecentlyAdded : SmartPlaylistRule {
        override val encode: String = "recently_added"
        override val label: String = "Recently added"
    }

    /** Songs played most recently, most recently played first. */
    data object RecentlyPlayed : SmartPlaylistRule {
        override val encode: String = "recently_played"
        override val label: String = "Recently played"
    }

    /** Songs that have never been played. */
    data object NeverPlayed : SmartPlaylistRule {
        override val encode: String = "never_played"
        override val label: String = "Never played"
    }

    /** Every song in [name]'s genre. */
    data class Genre(val name: String) : SmartPlaylistRule {
        override val encode: String = "genre:$name"
        override val label: String = "Genre · $name"
    }

    /** Every song by the artist identified by [artistId]. */
    data class Artist(val artistId: Long, val artistName: String) : SmartPlaylistRule {
        override val encode: String = "artist:$artistId:$artistName"
        override val label: String = "Artist · $artistName"
    }

    companion object {

        /** Builds a rule from its encoded string, or null when unrecognized. */
        fun parse(raw: String): SmartPlaylistRule? = when {
            raw == Favorites.encode -> Favorites
            raw == MostPlayed.encode -> MostPlayed
            raw == RecentlyAdded.encode -> RecentlyAdded
            raw == RecentlyPlayed.encode -> RecentlyPlayed
            raw == NeverPlayed.encode -> NeverPlayed
            raw.startsWith("genre:") -> Genre(raw.removePrefix("genre:"))
            raw.startsWith("artist:") -> parseArtist(raw)
            else -> null
        }

        private fun parseArtist(raw: String): SmartPlaylistRule? {
            val rest = raw.removePrefix("artist:")
            val id = rest.substringBefore(':').toLongOrNull() ?: return null
            val name = rest.substringAfter(':', "")
            if (id <= 0 || name.isBlank()) return null
            return Artist(artistId = id, artistName = name)
        }
    }
}
