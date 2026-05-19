package com.teamflow.planner.data;

import androidx.room.ColumnInfo;

/**
 * Single-column row for DISTINCT assignee queries.
 */
public class AssigneeName {

    @ColumnInfo(name = "assignee")
    public String assignee;
}
