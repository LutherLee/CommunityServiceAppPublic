package com.example.communityserviceapp.data.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * Base DAO implemented by other DAO that perform data invalidation
 */
interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(items: List<T>): List<Long>

    @Update
    suspend fun updateList(items: List<T>)

    @Delete
    suspend fun deleteList(items: List<T>)
}