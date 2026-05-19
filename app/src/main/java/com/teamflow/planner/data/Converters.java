package com.teamflow.planner.data;

import androidx.room.TypeConverter;

/**
 * Room type converters for enums used by entities.
 */
public class Converters {

    @TypeConverter
    public static String fromTaskStatus(TaskStatus value) {
        return value == null ? TaskStatus.PENDING.name() : value.name();
    }

    @TypeConverter
    public static TaskStatus toTaskStatus(String value) {
        if (value == null) {
            return TaskStatus.PENDING;
        }
        return TaskStatus.valueOf(value);
    }

    @TypeConverter
    public static String fromTaskPriority(TaskPriority value) {
        return value == null ? TaskPriority.MEDIUM.name() : value.name();
    }

    @TypeConverter
    public static TaskPriority toTaskPriority(String value) {
        if (value == null) {
            return TaskPriority.MEDIUM;
        }
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TaskPriority.MEDIUM;
        }
    }
}
