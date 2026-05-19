package com.teamflow.planner.data;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.teamflow.planner.data.entity.Task;

/**
 * Row for Smart Daily Focus: task plus its project name from a JOIN query.
 */
public class DailyFocusItem {

    @Embedded
    public Task task;

    @ColumnInfo(name = "projectName")
    public String projectName;
}
