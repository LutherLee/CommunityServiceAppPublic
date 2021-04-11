package com.example.communityserviceapp.util

import android.net.Uri
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.repository.*
import com.example.communityserviceapp.data.repository.AnnouncementRepository.Companion.announcementRealTimeListener
import com.example.communityserviceapp.data.repository.CrowdFundingRepository.Companion.crowdFundingDatabaseRef
import com.example.communityserviceapp.data.repository.CrowdFundingRepository.Companion.crowdFundingRealTimeListener
import com.example.communityserviceapp.data.repository.FaqRepository.Companion.faqDatabaseRef
import com.example.communityserviceapp.data.repository.FaqRepository.Companion.faqRealTimeListener
import com.example.communityserviceapp.data.repository.FavoriteRepository.Companion.userFavoriteRecipientDatabaseRef
import com.example.communityserviceapp.data.repository.FavoriteRepository.Companion.userFavoriteRecipientRealTimeListener
import com.example.communityserviceapp.data.repository.JobNatureRepository.Companion.jobNatureDatabaseRef
import com.example.communityserviceapp.data.repository.JobNatureRepository.Companion.jobNatureRealTimeListener
import com.example.communityserviceapp.util.Constants.FIREBASE_FIELD_UPDATED_AT
import com.example.communityserviceapp.util.Constants.NOTIFICATION_TYPE_ANNOUNCEMENT
import com.example.communityserviceapp.util.Constants.NOTIFICATION_TYPE_REMINDER
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.google.firebase.database.Query
import com.google.firebase.firestore.*
import com.google.firebase.firestore.Transaction
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A helper class with a collection of firebase methods used throughout the app
 */
val realTimeDatabaseInstance = FirebaseDatabase.getInstance()
val authInstance = FirebaseAuth.getInstance()
private var checkConnectivityListener: ValueEventListener? = null
private var checkConnectivityRef: DatabaseReference =
    realTimeDatabaseInstance.getReference("checkConnectivity")
val connectedRef = realTimeDatabaseInstance.getReference(".info/connected")
val currentFirebaseUser: FirebaseUser?
    get() = authInstance.currentUser

fun signOutOfFirebase() = authInstance.signOut()

suspend fun signInWithEmailAndPassword(emailAddress: String, password: String): Result<Any> =
    suspendCoroutine {
        authInstance.signInWithEmailAndPassword(emailAddress, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    it.resume(Result.Success())
                } else {
                    try {
                        throw task.exception!!
                    } catch (error: FirebaseTooManyRequestsException) {
                        it.resume(Result.Error(getString(R.string.error_too_many_requests)))
                    } catch (error: FirebaseAuthException) {
                        when (error.errorCode) {
                            "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_USER_DISABLED" -> {
                                it.resume(Result.Error("Invalid email or password"))
                            }
                            else -> {
                                it.resume(Result.Error(error.message!!))
                            }
                        }
                    } catch (error: FirebaseException) {
                        it.resume(Result.Error(error.message!!))
                    }
                }
            }
    }

suspend fun firebaseAuthWithGoogle(idToken: String): Result<Any> = suspendCoroutine {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    authInstance.signInWithCredential(credential).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            it.resume(Result.Success())
        } else {
            // Authenticate with firebase failed due to network error
            it.resume(Result.Error("Unable to login. Please check your connection and retry"))
        }
    }
}

suspend fun sendResetPasswordEmail(emailAddress: String): Result<Any> = suspendCoroutine {
    authInstance.sendPasswordResetEmail(emailAddress).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            it.resume(Result.Success())
        } else {
            try {
                throw task.exception!!
            } catch (malformedEmail: FirebaseAuthInvalidCredentialsException) {
                it.resume(Result.Error("Malformed Email"))
            } catch (invalidEmail: FirebaseAuthInvalidUserException) {
                val errorCode = invalidEmail.errorCode
                if (errorCode == "ERROR_USER_NOT_FOUND") {
                    it.resume(Result.Error("No matching account found"))
                } else if (errorCode == "ERROR_USER_DISABLED") {
                    it.resume(Result.Error("User account has been disabled"))
                }
            } catch (error: Exception) {
                it.resume(Result.Error(error.message!!))
            }
        }
    }
}

suspend fun createUserWithEmailAndPassword(emailAddress: String, password: String): Result<Any> =
    suspendCoroutine {
        authInstance.createUserWithEmailAndPassword(emailAddress, password)
            .addOnCompleteListener { task ->
                // Account is created at this point, check if sign in succeeded
                if (task.isSuccessful) {
                    it.resume(Result.Success())
                } else {
                    try {
                        throw task.exception!!
                    } catch (error: FirebaseAuthUserCollisionException) {
                        it.resume(Result.Error("Registration Failed! Email Exist"))
                    } catch (error: Exception) {
                        it.resume(
                            Result.Error(
                                getString(R.string.account_created_failed_update_account_details_error)
                            )
                        )
                    }
                }
            }
    }

suspend fun deleteFirebaseAccount(): Boolean = suspendCoroutine {
    currentFirebaseUser!!.delete().addOnCompleteListener { task ->
        if (task.isSuccessful) it.resume(true) else it.resume(false)
    }
}

suspend fun updateFirebaseAccountUsername(newUsername: String): Boolean {
    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newUsername).build()
    return performFirebaseProfileUpdate(profileUpdates)
}

suspend fun updateFirebaseAccountProfilePicture(profilePictureUri: Uri): Boolean {
    val profileUpdates = UserProfileChangeRequest.Builder().setPhotoUri(profilePictureUri).build()
    return performFirebaseProfileUpdate(profileUpdates)
}

private suspend fun performFirebaseProfileUpdate(profileUpdates: UserProfileChangeRequest): Boolean =
    suspendCoroutine {
        currentFirebaseUser!!.updateProfile(profileUpdates).addOnCompleteListener { task ->
            if (!task.isSuccessful) it.resume(false) else it.resume(true)
        }
    }

suspend fun updateFirebaseAccountEmail(newEmailAddress: String): Result<Any> = suspendCoroutine {
    currentFirebaseUser!!.updateEmail(newEmailAddress).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            it.resume(Result.Success())
        } else {
            try {
                throw task.exception!!
            } catch (malformedEmail: FirebaseAuthInvalidCredentialsException) {
                it.resume(Result.Error("Error: Malformed Email"))
            } catch (existEmail: FirebaseAuthUserCollisionException) {
                it.resume(Result.Error("Error: Email entered already exist"))
            } catch (e: Exception) {
                it.resume(
                    Result.Error(
                        getString(R.string.update_email_address_unsuccessful_text)
                    )
                )
            }
        }
    }
}

suspend fun updateFirebaseAccountPassword(newPassword: String): Result<Any> = suspendCoroutine {
    currentFirebaseUser!!.updatePassword(newPassword).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            it.resume(Result.Success())
        } else {
            it.resume(Result.Error(getString(R.string.error_updating_account_password)))
        }
    }
}

suspend fun addNotificationToFirestore(
    data: MutableMap<String, Any?>,
    notificationType: Int
): Boolean = suspendCoroutine {
    val ref = FirebaseFirestore.getInstance().collection("notifications")
    val documentID = ref.document().id
    if (notificationType == NOTIFICATION_TYPE_ANNOUNCEMENT) {
        data["announcementID"] = documentID
    } else if (notificationType == NOTIFICATION_TYPE_REMINDER) {
        data["reminderID"] = documentID
    }
    ref.document(documentID).set(data).addOnCompleteListener { task ->
        if (task.isSuccessful) it.resume(true) else it.resume(false)
    }
}

suspend fun getFirebaseServerTimestamp(): Result<Any> = suspendCoroutine {
    FirebaseFunctions.getInstance("asia-southeast2").getHttpsCallable("getTime").call()
        .addOnCompleteListener { httpsCallableResult: Task<HttpsCallableResult> ->
            if (httpsCallableResult.isSuccessful) {
                val timestamp = httpsCallableResult.result.data as Long
                it.resume(Result.Success(timestamp))
            } else {
                it.resume(Result.Error(httpsCallableResult.exception.toString()))
            }
        }
}

/**
 *  Attach a listener to 'checkConnectivity' node in realtime database, so when a reference to
 *  ".info/connected" is called by checkConnectionStatus() method, we can observe immediately
 *  whether internet connection is available by means of whether the app is connected to real-time database
 */
fun attachCheckConnectivityListener() {
    if (checkConnectivityListener == null) {
        Timber.d("Attaching check connectivity listener")
        checkConnectivityListener =
            checkConnectivityRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Do nothing - this listener just keeps the connection alive
                }

                override fun onCancelled(error: DatabaseError) {
                    Timber.d("Check Connectivity listener was cancelled")
                }
            })
    } else {
        Timber.d("Unable to attach check connectivity listener")
    }
}

fun removeCheckConnectivityListener() {
    if (checkConnectivityListener != null) {
        checkConnectivityRef.removeEventListener(checkConnectivityListener!!)
        checkConnectivityListener = null
        Timber.d("Check Connectivity listener removed")
    } else {
        Timber.d("Unable to remove Check Connectivity listener")
    }
}

suspend fun reauthenticateFirebaseUserAccount(password: String): Result<Any> = suspendCoroutine {
    currentFirebaseUser!!.reauthenticate(
        EmailAuthProvider.getCredential(currentFirebaseUser!!.email!!, password)
    ).addOnCompleteListener { task ->
        if (task.isSuccessful) {
            it.resume(Result.Success())
        } else {
            try {
                throw task.exception!!
            } catch (error: FirebaseTooManyRequestsException) {
                it.resume(Result.Error(getString(R.string.error_too_many_requests)))
            } catch (error: Exception) {
                it.resume(Result.Error(error.message!!))
            }
        }
    }
}

suspend fun addFeedbackMessageToFirestore(data: MutableMap<String, Any?>): Boolean =
    suspendCoroutine {
        val ref = FirebaseFirestore.getInstance().collection("feedback").document()
        ref.set(data).addOnCompleteListener { task ->
            if (!task.isSuccessful) it.resume(false) else it.resume(true)
        }
    }

suspend fun checkConnectionStatusToFirebase(): Boolean = suspendCoroutine {
    connectedRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.getValue(Boolean::class.java)!!) {
                it.resume(true)
            } else {
                it.resume(false)
            }
        }

        override fun onCancelled(error: DatabaseError) {
            it.resume(false)
        }
    })
}

suspend fun saveRecipientAsFavoriteOrUnfavourite(
    data: Map<String, Any?>,
    userID: String,
    recipientID: String
): Boolean = suspendCoroutine {
    val ref =
        realTimeDatabaseInstance.reference.child("userFavorites").child(userID).child(recipientID)
    ref.setValue(data).addOnCompleteListener { task ->
        if (task.isSuccessful) it.resume(true) else it.resume(false)
    }
}

suspend fun subscribeToFCMTopic(topicName: String): Boolean = suspendCoroutine {
    FirebaseMessaging.getInstance().subscribeToTopic(topicName).addOnCompleteListener { task ->
        if (task.isSuccessful) it.resume(true) else it.resume(false)
    }
}

suspend fun updateRecipientReview(
    recipientReviewData: MutableMap<String, Any?>,
    recipientID: String,
    userRating: Float,
    currentUserID: String
): Result<Any> = suspendCoroutine {
    val recipientItemRef =
        FirebaseFirestore.getInstance().collection("recipients").document(recipientID)
    var newAverageRating = 0.0

    FirebaseFirestore.getInstance().runTransaction<Any> { transaction ->
        val snapshot = transaction[recipientItemRef]
        val currentTotalReviews = snapshot.getDouble("totalReviews")!!
        val currentAverageRating = snapshot.getDouble("recipientRating")!!
        val newTotalRatings = currentTotalReviews + 1
        newAverageRating =
            (currentTotalReviews * currentAverageRating + userRating) / newTotalRatings

        if (newAverageRating in 0.0..5.0) {
            recordRecipientItem(transaction, newAverageRating, userRating, recipientItemRef)
            recordRecipientReview(transaction, recipientReviewData, recipientID)
            recordUserRatingForRecipient(transaction, recipientID, currentUserID, userRating)
            return@runTransaction null
        } else {
            throw FirebaseFirestoreException(
                "Invalid rating value",
                FirebaseFirestoreException.Code.ABORTED
            )
        }
    }.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val userReviewMetadata = mutableMapOf<String, Any?>()
            userReviewMetadata["newAverageRating"] = newAverageRating
            userReviewMetadata["userRating"] = userRating.toInt()
            userReviewMetadata["navigateToReviewRecipientBottomSheet"] = false
            it.resume(Result.Success(userReviewMetadata))
        } else {
            it.resume(Result.Error())
        }
    }
}

fun recordRecipientItem(
    transaction: Transaction,
    newAverageRating: Double,
    userRating: Float,
    recipientItemRef: DocumentReference
) {
    val recipientItemData: MutableMap<String, Any> = HashMap()
    recipientItemData["recipientRating"] = newAverageRating
    recipientItemData["totalReviews"] = FieldValue.increment(1)
    when (userRating.toInt()) {
        5 -> {
            recipientItemData["numOf5Star"] = FieldValue.increment(1)
        }
        4 -> {
            recipientItemData["numOf4Star"] = FieldValue.increment(1)
        }
        3 -> {
            recipientItemData["numOf3Star"] = FieldValue.increment(1)
        }
        2 -> {
            recipientItemData["numOf2Star"] = FieldValue.increment(1)
        }
        1 -> {
            recipientItemData["numOf1Star"] = FieldValue.increment(1)
        }
    }
    recipientItemData["updatedAt"] = Timestamp.now().seconds
    transaction.update(recipientItemRef, recipientItemData)
}

fun recordRecipientReview(
    transaction: Transaction,
    data: MutableMap<String, Any?>,
    recipientID: String
) {
    val ref = FirebaseFirestore.getInstance().collection("recipients").document(recipientID)
        .collection("recipientReviews")
    val documentID = ref.document().id
    data["reviewID"] = documentID
    val updatedRef = ref.document(documentID)
    transaction.set(updatedRef, data)
}

private fun recordUserRatingForRecipient(
    transaction: Transaction,
    recipientID: String,
    currentUserID: String,
    userRating: Float
) {
    val data: MutableMap<String, Any?> = HashMap()
    data["userID"] = currentUserID
    data["ratingValue"] = userRating
    data["reviewDate"] = Timestamp.now()
    data["reviewOn"] = recipientID
    val ref = FirebaseFirestore.getInstance().collection("userReviews").document(currentUserID)
        .collection("allReviews").document()
    transaction.set(ref, data)
}

fun storeUserRecommendationCriteria(data: Map<String, Any?>, userID: String) {
    realTimeDatabaseInstance.reference.child("userRecommendationCriteria")
        .child(userID).setValue(data).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                // Retry if unsuccessful
                storeUserRecommendationCriteria(data, userID)
            }
        }
}

suspend fun deleteFirebaseUserAllFavorites(userID: String, data: Map<String, Any?>) =
    suspendCoroutine<Boolean> {
        val ref = realTimeDatabaseInstance.reference.child("userFavorites").child(userID)
        ref.child("deleteAll").setValue(data).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                it.resume(false)
            } else {
                GlobalScope.launch { updateUserAllFavoritesAsDeleted(ref) }
            }
        }
    }

private suspend fun updateUserAllFavoritesAsDeleted(ref: DatabaseReference) =
    suspendCoroutine<Boolean> {
        val query = ref.orderByChild("isDeleted").equalTo(false)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (ds in dataSnapshot.children) {
                    ds.child("isDeleted").ref.setValue(true)
                }
                it.resume(true)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                it.resume(false)
                Timber.w("Failed to delete all user favorites due to problem with attaching listener")
            }
        })
    }

fun <T> setupFirestoreCollectionGroupPath(
    firebaseDataType: FirebaseDataType.Firestore.WithCollectionGroup<T>,
    lastDataFetchTimestamp: Long
) {
    when (firebaseDataType) {
        is FirebaseDataType.Firestore.WithCollectionGroup.FDTReview -> {
            getRecipientReviews(firebaseDataType, lastDataFetchTimestamp)
        }
        is FirebaseDataType.Firestore.WithCollectionGroup.FDTAnnouncement -> {
            val query = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("notificationType", "announcement")
                .whereGreaterThan(FIREBASE_FIELD_UPDATED_AT, lastDataFetchTimestamp)
                .orderBy(FIREBASE_FIELD_UPDATED_AT)
                .limitToLast(25)

            announcementRealTimeListener = attachFirestoreRealTimeListenerByCustomQuery(
                firebaseDataType,
                query
            )
        }
    }
}

private fun <T> attachFirestoreRealTimeListenerByCustomQuery(
    firebaseDataType: FirebaseDataType.Firestore<T>,
    query: com.google.firebase.firestore.Query
): ListenerRegistration {
    return query.addSnapshotListener { querySnapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
        when {
            error != null -> Timber.w("${firebaseDataType.name} Real-time Listener listen failed. $error")
            querySnapshot!!.isEmpty -> Timber.d("No ${firebaseDataType.name} cache inconsistencies")
            else -> {
                Timber.d("${firebaseDataType.name} real-time listener listen successful")
                GlobalScope.launch {
                    identifyDataInconsistencies(
                        querySnapshot,
                        null,
                        firebaseDataType
                    )
                }
            }
        }
    }
}

fun <T> attachFirestoreRealTimeListener(
    firebaseDataType: FirebaseDataType.Firestore<T>,
    lastDataFetchTimestamp: Long
): ListenerRegistration {
    val ref = FirebaseFirestore.getInstance().collection(firebaseDataType.path)
        .whereGreaterThan(FIREBASE_FIELD_UPDATED_AT, lastDataFetchTimestamp)

    return ref.addSnapshotListener { querySnapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
        when {
            error != null -> Timber.w("${firebaseDataType.name} Real-time Listener listen failed. $error")
            querySnapshot!!.isEmpty -> Timber.d("No ${firebaseDataType.name} cache inconsistencies")
            else -> {
                Timber.d("${firebaseDataType.name} real-time listener listen successful")
                GlobalScope.launch {
                    identifyDataInconsistencies(
                        querySnapshot,
                        null,
                        firebaseDataType
                    )
                }
            }
        }
    }
}

fun <T> setupFirebaseDatabaseReferencePath(
    firebaseDataType: FirebaseDataType.RealTimeDatabase<T>,
    lastDataFetchTimestamp: Long
) {
    when (firebaseDataType) {
        is FirebaseDataType.RealTimeDatabase.FDTFaq -> {
            faqDatabaseRef = realTimeDatabaseInstance.reference.child(firebaseDataType.path)
                .orderByChild(FIREBASE_FIELD_UPDATED_AT)
                .startAt(lastDataFetchTimestamp.toDouble())

            faqRealTimeListener = attachFirebaseDatabaseRealTimeListener(
                faqDatabaseRef!!,
                firebaseDataType
            )
        }
        is FirebaseDataType.RealTimeDatabase.FDTFavorite -> {
            userFavoriteRecipientDatabaseRef = if (lastDataFetchTimestamp == 0L) {
                Timber.d("First time user favorites fetch")
                realTimeDatabaseInstance.reference.child(firebaseDataType.path)
                    .child(firebaseDataType.associatedID!!).orderByChild("isDeleted")
                    .equalTo(false)
            } else {
                realTimeDatabaseInstance.reference.child(firebaseDataType.path)
                    .child(firebaseDataType.associatedID!!).orderByChild(FIREBASE_FIELD_UPDATED_AT)
                    .startAt(lastDataFetchTimestamp.toDouble())
            }

            userFavoriteRecipientRealTimeListener = attachFirebaseDatabaseRealTimeListener(
                userFavoriteRecipientDatabaseRef!!, firebaseDataType
            )
        }
        is FirebaseDataType.RealTimeDatabase.FDTJobNature -> {
            jobNatureDatabaseRef = realTimeDatabaseInstance.reference.child(firebaseDataType.path)
                .orderByChild(FIREBASE_FIELD_UPDATED_AT)
                .startAt(lastDataFetchTimestamp.toDouble())

            jobNatureRealTimeListener = attachFirebaseDatabaseRealTimeListener(
                jobNatureDatabaseRef!!,
                firebaseDataType
            )
        }
        is FirebaseDataType.RealTimeDatabase.FDTCrowdFunding -> {
            crowdFundingDatabaseRef =
                realTimeDatabaseInstance.reference.child(firebaseDataType.path)
                    .orderByChild(FIREBASE_FIELD_UPDATED_AT)
                    .startAt(lastDataFetchTimestamp.toDouble())

            crowdFundingRealTimeListener = attachFirebaseDatabaseRealTimeListener(
                crowdFundingDatabaseRef!!,
                firebaseDataType
            )
        }
    }
}

/**
 * Specify query to get document at "faqs" node by which "updatedAt" field
 * value is greater than last faq fetch timestamp
 */
private fun <T> attachFirebaseDatabaseRealTimeListener(
    databaseReference: Query,
    firebaseDataType: FirebaseDataType.RealTimeDatabase<T>
): ValueEventListener {
    return databaseReference.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(dataSnapshot: DataSnapshot) {
            // Method called once with the initial value and again whenever data at this location is updated
            if (dataSnapshot.exists()) {
                GlobalScope.launch {
                    identifyDataInconsistencies(
                        null,
                        dataSnapshot,
                        firebaseDataType
                    )
                }
            } else {
                Timber.d("No ${firebaseDataType.name} cache inconsistencies")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Timber.w("Failed to attach ${firebaseDataType.name} listener")
        }
    })
}

private fun <T> getRecipientReviews(
    firebaseDataType: FirebaseDataType.Firestore.WithCollectionGroup<T>,
    lastDataFetchTimestamp: Long
) {
    FirebaseFirestore.getInstance().collectionGroup(firebaseDataType.path)
        .whereEqualTo("reviewOn", firebaseDataType.associatedID)
        .whereGreaterThan(FIREBASE_FIELD_UPDATED_AT, lastDataFetchTimestamp)
        .orderBy(
            FIREBASE_FIELD_UPDATED_AT,
            com.google.firebase.firestore.Query.Direction.DESCENDING
        )
        .limit(75)
        .get()
        .addOnSuccessListener { querySnapshot ->
            if (querySnapshot.isEmpty) {
                Timber.d("No ${firebaseDataType.name} cache inconsistencies")
                onRecipientReviewLoadedListener?.invoke()
            } else {
                Timber.d("${firebaseDataType.name} real-time listener listen successful")
                GlobalScope.launch {
                    identifyDataInconsistencies(
                        querySnapshot,
                        null,
                        firebaseDataType
                    )
                }
            }
        }
        .addOnFailureListener { exception ->
            Timber.w("${firebaseDataType.name} Real-time Listener listen failed. $exception")
            onRecipientReviewLoadedListener?.invoke()
        }
}
