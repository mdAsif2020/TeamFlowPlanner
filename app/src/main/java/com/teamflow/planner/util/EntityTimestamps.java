package com.teamflow.planner.util;

import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;

/**
 * Sets {@code lastModified} for sync conflict resolution (latest wins).
 */
public final class EntityTimestamps {

    private EntityTimestamps() {
    }

    public static void touch(Task task) {
        if (task != null) {
            task.lastModified = System.currentTimeMillis();
        }
    }

    public static void touch(Project project) {
        if (project != null) {
            project.lastModified = System.currentTimeMillis();
        }
    }
}
