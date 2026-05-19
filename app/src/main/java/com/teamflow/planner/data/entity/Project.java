package com.teamflow.planner.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A project groups related tasks. {@code isCompleted} is set automatically when every task is done.
 */
@Entity(tableName = "projects")
public class Project {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name;

    @NonNull
    public String description;

    /** Email of the user who created the project. */
    public String ownerEmail;

    /** True when the project has at least one task and all tasks are completed. */
    public boolean isCompleted;

    /** Manual pin for the dashboard's main progress highlight. */
    public boolean isPinned;

    /** Creation time (epoch millis) for stable ordering on the dashboard. */
    public long createdAt;

    /** Last local or merged modification time (epoch millis) for cloud sync. */
    public long lastModified;

    /** ID of the project in the remote database (Supabase). */
    public Long remoteId;

    /** Required by Room. */
    public Project() {
        this.name = "";
        this.description = "";
        this.ownerEmail = "";
    }

    @Ignore
    public Project(@NonNull String name, @NonNull String description, boolean isCompleted, long createdAt, String ownerEmail) {
        this.name = name;
        this.description = description;
        this.isCompleted = isCompleted;
        this.createdAt = createdAt;
        this.ownerEmail = ownerEmail;
        this.lastModified = System.currentTimeMillis();
    }
}
