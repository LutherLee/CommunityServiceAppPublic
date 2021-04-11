package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crowdFunding")
data class CrowdFunding(
    @PrimaryKey @ColumnInfo(name = "id") val crowdFundingID: String = "",
    val name: String = "",
    val detail: String = "",
    val donationPolicy: String = "",
    val websiteLink: String = "",
    val imageUrl: String = "",
    val instagram: String = "",
    val linkedin: String = "",
    val facebook: String = "",
    val twitter: String = "",
    val youtube: String = "",
    val updatedAt: Long = 0L
)
