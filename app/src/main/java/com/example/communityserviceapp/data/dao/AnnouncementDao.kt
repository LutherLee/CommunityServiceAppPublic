package com.example.communityserviceapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.example.communityserviceapp.data.model.Announcement

@Dao
interface AnnouncementDao : BaseDao<Announcement> {
    @Query("SELECT * FROM announcement ORDER BY updatedAt DESC")
    fun getPagedAnnouncements(): PagingSource<Int, Announcement>
}
