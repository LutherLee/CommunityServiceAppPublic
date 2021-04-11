package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "banner")
data class Banner(
    @PrimaryKey @ColumnInfo(name = "id") val bannerID: String = "",
    @ColumnInfo(name = "name") val bannerName: String = "",
    @ColumnInfo(name = "detail") val bannerDetail: String = "",
    @ColumnInfo(name = "websiteLink") val bannerWebsiteLink: String = "",
    @ColumnInfo(name = "imageUrl") val bannerImageUrl: String = "",
    val updatedAt: Long = 0L
)
