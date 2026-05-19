package com.teamflow.planner.data;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Relation;

import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.TeamMember;

import java.util.List;

/**
 * Project row with aggregate task counts for dashboard cards.
 */
public class ProjectListItem {

    @Embedded
    public Project project;

    @ColumnInfo(name = "taskCount")
    public int taskCount;

    @ColumnInfo(name = "completedCount")
    public int completedCount;

    @Relation(
            parentColumn = "id",
            entityColumn = "projectId"
    )
    public List<TeamMember> members;
}
