package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobNature")
data class JobNature(
    @ColumnInfo(name = "id") val jobNatureID: String = "",
    @PrimaryKey val type: String = "",
    val updatedAt: Long = 0
)
