package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faq")
data class Faq(
    @PrimaryKey @ColumnInfo(name = "id") val faqID: String = "",
    @ColumnInfo(name = "question") val faqQuestion: String = "",
    @ColumnInfo(name = "answer") val faqAnswer: String = "",
    val updatedAt: Long = 0
)