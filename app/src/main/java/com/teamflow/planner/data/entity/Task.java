package com.teamflow.planner.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.teamflow.planner.data.TaskPriority;
import com.teamflow.planner.data.TaskStatus;

/**
 * A task belongs to a {@link Project}. Deleting a project cascades to its tasks.
 */
@Entity(
        tableName = "tasks",
        foreignKeys = @ForeignKey(
                entity = Project.class,
                parentColumns = "id",
                childColumns = "projectId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("projectId")}
)
public class Task {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long projectId;

    @NonNull
    public String title;

    @NonNull
    public String description;

    /** Extra notes for the assignee or team. */
    @NonNull
    public String notes;

    @NonNull
    public TaskPriority priority;

    /** Locally stored assignee name (plain text). */
    @NonNull
    public String assignee;

    /** Start of the chosen deadline day in local time (epoch millis), or null if none. */
    public Long deadline;

    @NonNull
    public TaskStatus status;

    /** Last local or merged modification time (epoch millis) for cloud sync. */
    public long lastModified;

    /** Required by Room. */
    public Task() {
        this.title = "";
        this.description = "";
        this.notes = "";
        this.priority = TaskPriority.MEDIUM;
        this.assignee = "";
        this.status = TaskStatus.PENDING;
    }

    @Ignore
    public Task(long projectId, @NonNull String title, @NonNull String assignee, Long deadline, @NonNull TaskStatus status) {
        this.projectId = projectId;
        this.title = title;
        this.description = "";
        this.notes = "";
        this.priority = TaskPriority.MEDIUM;
        this.assignee = assignee;
        this.deadline = deadline;
        this.status = status;
    }
}
