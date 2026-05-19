package com.teamflow.planner.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.teamflow.planner.data.entity.Notification;

import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(Notification notification);

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    LiveData<List<Notification>> observeAll();

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    LiveData<Integer> observeUnreadCount();

    @Update
    void update(Notification notification);

    @Query("UPDATE notifications SET isRead = 1")
    void markAllAsRead();

    @Query("SELECT COUNT(*) > 0 FROM notifications WHERE remoteId = :remoteId")
    boolean exists(String remoteId);
}
