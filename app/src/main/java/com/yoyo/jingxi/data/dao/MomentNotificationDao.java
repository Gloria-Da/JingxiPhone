package com.yoyo.jingxi.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.yoyo.jingxi.data.entity.MomentNotification;

import java.util.List;

@Dao
public interface MomentNotificationDao {
    @Insert
    long insert(MomentNotification notification);

    @Delete
    void delete(MomentNotification notification);

    @Query("SELECT * FROM moment_notifications WHERE receiverId = :receiverId AND receiverType = :receiverType ORDER BY timestamp DESC")
    LiveData<List<MomentNotification>> getNotificationsForReceiver(String receiverId, int receiverType);

    @Query("SELECT * FROM moment_notifications WHERE receiverId = :receiverId AND receiverType = :receiverType ORDER BY timestamp DESC")
    List<MomentNotification> getNotificationsForReceiverSync(String receiverId, int receiverType);

    @Query("SELECT * FROM moment_notifications ORDER BY timestamp DESC")
    List<MomentNotification> getAllNotificationsSync();

    @Query("SELECT COUNT(*) FROM moment_notifications WHERE receiverId = :receiverId AND receiverType = :receiverType AND isRead = 0")
    LiveData<Integer> getUnreadCount(String receiverId, int receiverType);

    @Query("UPDATE moment_notifications SET isRead = 1 WHERE receiverId = :receiverId AND receiverType = :receiverType AND isRead = 0")
    void markAllAsRead(String receiverId, int receiverType);
}
