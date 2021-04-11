package com.example.communityserviceapp.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "recipient")
data class Recipient(
    @PrimaryKey @ColumnInfo(name = "id") val recipientID: String = "",
    @ColumnInfo(name = "name") val recipientName: String = "",
    @ColumnInfo(name = "detail") val recipientDetail: String = "",
    @ColumnInfo(name = "jobNature") val recipientJobNature: String = "",
    @ColumnInfo(name = "state") val recipientState: String = "",
    @ColumnInfo(name = "rating") val recipientRating: Double = 0.0,
    @ColumnInfo(name = "yearEstablished") val recipientYearEstablished: Int = 0,
    @ColumnInfo(name = "address") val recipientAddress: String = "",
    @ColumnInfo(name = "email") val recipientEmail: String = "",
    @ColumnInfo(name = "website") val recipientWebsite: String = "",
    @ColumnInfo(name = "phoneNum") val recipientPhoneNum: String = "",
    @ColumnInfo(name = "faxNum") val recipientFaxNum: String = "",
    @ColumnInfo(name = "imageUrl") val recipientImageUrl: String = "",
    @ColumnInfo(name = "registrationNum") val recipientRegistrationNum: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val instagram: String = "",
    val linkedin: String = "",
    val facebook: String = "",
    val twitter: String = "",
    val vimeo: String = "",
    val youtube: String = "",
    val pinterest: String = "",
    val flickr: String = "",
    val updatedAt: Long = 0,
    val totalReviews: Int = 0,
    val numOf5Star: Int = 0,
    val numOf4Star: Int = 0,
    val numOf3Star: Int = 0,
    val numOf2Star: Int = 0,
    val numOf1Star: Int = 0
) : SearchSuggestion, ClusterItem {

    // Override methods from SearchSuggestion interface
    // Usage: For recipient suggestion by custom SearchView in "HomeFragment" and "RecipientSearchAndFilterFragment"
    override fun getBody(): String = recipientName

    override fun describeContents(): Int = 0

    // Override methods from ClusterItem interface
    // Usage: As accessor method of cluster item metadata which is managed by cluster manager in "MapFragment"
    override fun getPosition(): LatLng = LatLng(latitude, longitude)

    override fun getTitle(): String = recipientName

    override fun getSnippet(): String = recipientAddress
}
