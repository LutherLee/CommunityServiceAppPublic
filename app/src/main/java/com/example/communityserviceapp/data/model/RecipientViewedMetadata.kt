package com.example.communityserviceapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recipientViewedMetadata",
    primaryKeys = ["userID", "recipientID"],
    foreignKeys = [
        ForeignKey(
            entity = Recipient::class,
            parentColumns = ["id"],
            childColumns = ["recipientID"]
        )
    ],
    indices = [Index(value = ["recipientID"])]
)
class RecipientViewedMetadata(
    val userID: String,
    val recipientID: String
)
