package com.example.communityserviceapp.data.model

import androidx.room.Entity

/**
 * No Foreign Key is added based on [recipientID] so as to prevent SQL Foreign Key Constraint
 * Exception when performing database maintanence and user did not update the app
 */
@Entity(
    tableName = "favorite",
    primaryKeys = ["userID", "recipientID"]
)
data class Favorite(
    val userID: String = "",
    val recipientID: String = "",
    val updatedAt: Long = 0
)
