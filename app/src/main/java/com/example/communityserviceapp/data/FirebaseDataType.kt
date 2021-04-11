package com.example.communityserviceapp.data

import com.example.communityserviceapp.data.dao.BaseDao
import com.example.communityserviceapp.data.model.*

/**
 * Sealed Class used by methods to only accept data type (with or without collection group)
 * used by Firebase only, either Firestore or RealTimeDatabase.
 *
 * Note: All data class name is appended with 'FDT' to represent Data Type used in Firebase
 */
sealed class FirebaseDataType<T> {
    abstract val path: String
    abstract val name: String
    abstract val associatedClass: Class<T>
    abstract val associatedID: String? // Can be userID or recipientID
    abstract val dao: BaseDao<T>

    sealed class Firestore<T> : FirebaseDataType<T>() {
        data class FDTBanner(
            override val path: String = "banners",
            override val name: String = "Banner",
            override val associatedID: String? = null,
            override val associatedClass: Class<Banner> = Banner::class.java,
            override val dao: BaseDao<Banner>
        ) : Firestore<Banner>()

        data class FDTRecipient(
            override val path: String = "recipients",
            override val name: String = "Recipient",
            override val associatedID: String? = null,
            override val associatedClass: Class<Recipient> = Recipient::class.java,
            override val dao: BaseDao<Recipient>
        ) : Firestore<Recipient>()

        sealed class WithCollectionGroup<T> : Firestore<T>() {
            data class FDTAnnouncement(
                override val path: String = "notifications",
                override val name: String = "Announcement",
                override val associatedID: String? = null,
                override val associatedClass: Class<Announcement> = Announcement::class.java,
                override val dao: BaseDao<Announcement>
            ) : WithCollectionGroup<Announcement>()

            data class FDTReview(
                override val path: String = "recipientReviews",
                override val name: String = "Recipient Review",
                override val associatedID: String?,
                override val associatedClass: Class<Review> = Review::class.java,
                override val dao: BaseDao<Review>
            ) : WithCollectionGroup<Review>()
        }
    }

    sealed class RealTimeDatabase<T> : FirebaseDataType<T>() {
        data class FDTFaq(
            override val path: String = "faqs",
            override val name: String = "Faq",
            override val associatedClass: Class<Faq> = Faq::class.java,
            override val associatedID: String? = null,
            override val dao: BaseDao<Faq>
        ) : RealTimeDatabase<Faq>()

        data class FDTFavorite(
            override val path: String = "userFavorites",
            override val name: String = "User Favorite Recipient",
            override val associatedClass: Class<Favorite> = Favorite::class.java,
            override val associatedID: String?,
            override val dao: BaseDao<Favorite>
        ) : RealTimeDatabase<Favorite>()

        data class FDTJobNature(
            override val path: String = "jobNatures",
            override val name: String = "Recipient Job Nature",
            override val associatedClass: Class<JobNature> = JobNature::class.java,
            override val associatedID: String? = null,
            override val dao: BaseDao<JobNature>
        ) : RealTimeDatabase<JobNature>()

        data class FDTCrowdFunding(
            override val path: String = "crowdFundings",
            override val name: String = "Crowd Funding",
            override val associatedClass: Class<CrowdFunding> = CrowdFunding::class.java,
            override val associatedID: String? = null,
            override val dao: BaseDao<CrowdFunding>
        ) : RealTimeDatabase<CrowdFunding>()
    }
}
