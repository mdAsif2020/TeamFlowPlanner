package com.teamflow.planner.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Name-based team member roster for a project (used for assignee suggestions).
 */
@Entity(
        tableName = "team_members",
        foreignKeys = @ForeignKey(
                entity = Project.class,
                parentColumns = "id",
                childColumns = "projectId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("projectId")}
)
public class TeamMember {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long projectId;

    @NonNull
    public String name;

    public long createdAt;

    public TeamMember() {
        this.name = "";
    }
}
