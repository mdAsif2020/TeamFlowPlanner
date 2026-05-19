package com.teamflow.planner.ui;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.ui.adapter.TaskAdapter;
import com.teamflow.planner.util.EntityTimestamps;
import com.teamflow.planner.util.ProjectCompletionHelper;
import com.teamflow.planner.util.StreakTracker;

import java.util.concurrent.Executor;

/**
 * Swipe shortcuts: right → Completed, left → In Progress.
 */
public class TaskSwipeCallback extends ItemTouchHelper.SimpleCallback {

    private final TaskAdapter adapter;
    private final AppDatabase db;
    private final Executor io;
    private final Handler main;
    private final Context appContext;

    public TaskSwipeCallback(
            TaskAdapter adapter,
            AppDatabase db,
            Context context,
            Executor io,
            Handler mainHandler
    ) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.adapter = adapter;
        this.db = db;
        this.appContext = context.getApplicationContext();
        this.io = io;
        this.main = mainHandler;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        Task task = adapter.getItem(position);
        TaskStatus previous = task.status;
        TaskStatus next;
        if (direction == ItemTouchHelper.RIGHT) {
            next = TaskStatus.COMPLETED;
        } else {
            next = TaskStatus.IN_PROGRESS;
        }
        task.status = next;
        EntityTimestamps.touch(task);

        io.execute(() -> {
            db.taskDao().update(task);
            ProjectCompletionHelper.refreshProjectCompletion(db, task.projectId);
            if (next == TaskStatus.COMPLETED && previous != TaskStatus.COMPLETED) {
                StreakTracker.onTaskCompleted(appContext);
            }
            main.post(() -> adapter.notifyItemChanged(position));
        });
    }
}
