package com.prakash.pmusic.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit schema migrations, one object per version step.
 *
 * The SQL must reproduce exactly what Room would generate for the entities
 * declared in [PMusicDatabase] at that version, so runtime migration
 * validation passes and the exported schema stays in sync.
 */
object Migrations {

    /** v1 → v2: adds user playlists and the playlist-song join table. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_songs` (
                    `playlistId` INTEGER NOT NULL,
                    `songId` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `songId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`songId`) REFERENCES `songs`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` ON `playlist_songs` (`songId`)"
            )
        }
    }

    /** v2 → v3: adds the nullable rule column for smart playlists. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `rule` TEXT")
        }
    }
}
