package com.example.communityserviceapp.data.model

import androidx.room.*
import com.google.firebase.firestore.ServerTimestamp
import java.util.*

@Entity(
    tableName = "review",
    foreignKeys = [
        ForeignKey(
            entity = Recipient::class,
            parentColumns = ["id"],
            childColumns = ["reviewOn"]
        )
    ],
    indices = [Index(value = ["reviewOn"])]
)
data class Review(
    @PrimaryKey @ColumnInfo(name = "id") val reviewID: String = "",
    @ServerTimestamp @ColumnInfo(name = "date") val reviewDate: Date? = null,
    @ColumnInfo(name = "message") val reviewMessage: String = "",
    @ColumnInfo(name = "tags") val reviewTags: String = "",
    val reviewerName: String = "",
    val reviewerID: String = "",
    val reviewerImageUrl: String? = null,
    val reviewOn: String = "",
    val reviewRating: Float = 0.0f,
    val updatedAt: Long = 0
)
