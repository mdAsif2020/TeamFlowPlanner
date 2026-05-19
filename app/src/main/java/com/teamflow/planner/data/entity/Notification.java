package com.teamflow.planner.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class Notification {
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public String title;
    public String message;
    public long timestamp;
    public boolean isRead;
    public String type; // "INVITATION", "TASK_UPDATE", "MEMBER_UPDATE"
    public Long projectId;
    public String remoteId; // For cloud sync if needed

    public Notification() {}

    public Notification(String title, String message, String type) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
    }
}
