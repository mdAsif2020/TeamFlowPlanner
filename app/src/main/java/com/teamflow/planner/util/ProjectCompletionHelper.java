package com.teamflow.planner.util;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;

import java.util.List;

/**
 * Keeps {@link Project#isCompleted} in sync when task statuses change.
 * Must be called on a background thread (Room does not allow main-thread queries here).
 */
public final class ProjectCompletionHelper {

    private ProjectCompletionHelper() {
    }

    public static void refreshProjectCompletion(AppDatabase db, long projectId) {
        List<Task> tasks = db.taskDao().getTasksForProjectSync(projectId);
        Project project = db.projectDao().getProjectById(projectId);
        if (project == null) {
            return;
        }
        boolean allDone = !tasks.isEmpty();
        for (Task t : tasks) {
            if (t.status != TaskStatus.COMPLETED) {
                allDone = false;
                break;
            }
        }
        if (tasks.isEmpty()) {
            allDone = false;
        }
        if (project.isCompleted != allDone) {
            project.isCompleted = allDone;
            EntityTimestamps.touch(project);
            db.projectDao().update(project);
        }
    }
}
