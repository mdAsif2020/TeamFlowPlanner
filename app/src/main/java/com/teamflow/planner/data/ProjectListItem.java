package com.teamflow.planner.data;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.teamflow.planner.data.entity.Project;

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
}
