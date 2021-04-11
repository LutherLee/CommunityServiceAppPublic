package com.example.communityserviceapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recommendationMetadata",
    primaryKeys = ["userID", "jobNature"],
    foreignKeys = [
        ForeignKey(
            entity = JobNature::class,
            parentColumns = ["type"],
            childColumns = ["jobNature"]
        )
    ],
    indices = [Index(value = ["jobNature"])]
)
data class RecommendationMetadata(
    val userID: String,
    val jobNature: String,
    val weight: Int
)
