package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

@Entity(tableName = "announcement")
data class Announcement(
    @PrimaryKey @ColumnInfo(name = "id") val announcementID: String = "",
    @ColumnInfo(name = "title") val announcementTitle: String = "",
    @ColumnInfo(name = "message") val announcementMessage: String = "",
    @ServerTimestamp @ColumnInfo(name = "createdDate") val announcementCreatedDate: Date? = null,
    val senderUID: String = "",
    val updatedAt: Long = 0
)
