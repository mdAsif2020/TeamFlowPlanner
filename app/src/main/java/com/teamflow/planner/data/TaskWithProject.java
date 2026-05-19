package com.teamflow.planner.data;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.teamflow.planner.data.entity.Task;

/**
 * Task row with its project name for cross-project member views.
 */
public class TaskWithProject {

    @Embedded
    public Task task;

    @ColumnInfo(name = "projectName")
    public String projectName;
}
