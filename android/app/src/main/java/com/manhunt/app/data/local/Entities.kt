package com.manhunt.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val avatarEmoji: String,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val catchesMade: Int = 0
)

@Entity(tableName = "match_history")
data class MatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val matchId: Long = 0,
    val joinCode: String,
    val rolePlayed: String,
    val isWin: Boolean,
    val durationSeconds: Int,
    val catchesCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)
