package com.teamflow.planner.data;

/**
 * Task workflow states stored in Room (via {@link com.teamflow.planner.data.Converters}).
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}
