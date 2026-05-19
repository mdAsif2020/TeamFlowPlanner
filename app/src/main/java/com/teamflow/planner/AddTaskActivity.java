package com.teamflow.planner;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.MaterialDatePicker;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.TaskPriority;
import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.databinding.ActivityAddTaskBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.UserSearchAdapter;
import com.teamflow.planner.util.EntityTimestamps;
import com.teamflow.planner.util.ProjectCompletionHelper;
import com.teamflow.planner.util.StreakTracker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Create or edit a task: description, notes, priority, assignee (with team roster hints), deadline, status.
 */
public class AddTaskActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    private ActivityAddTaskBinding binding;
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long projectId;
    private Long editingTaskId;
    private Long selectedDeadlineMillis;
    private final SimpleDateFormat previewFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    private String[] statusLabels;
    private String[] priorityLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1L);
        if (projectId <= 0) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        statusLabels = new String[]{
                getString(R.string.status_pending),
                getString(R.string.status_in_progress),
                getString(R.string.status_completed)
        };
        binding.autoCompleteStatus.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, statusLabels));

        priorityLabels = new String[]{
                getString(R.string.priority_low),
                getString(R.string.priority_medium),
                getString(R.string.priority_high)
        };
        binding.autoCompletePriority.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, priorityLabels));
        
        binding.autoCompletePriority.setText(priorityLabels[1], false);

        db.teamMemberDao().observeMembersForProject(projectId).observe(this, members -> {
            List<String> suggestions = new ArrayList<>();
            if (members != null) {
                for (TeamMember m : members) {
                    if (m.username != null && !m.username.isEmpty()) {
                        suggestions.add(m.username);
                    } else if (m.name != null && !m.name.isEmpty()) {
                        suggestions.add(m.name);
                    }
                }
            }
            binding.inputAssignee.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, suggestions));
        });

        long taskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1L);
        if (taskId > 0) {
            editingTaskId = taskId;
            binding.toolbar.setTitle(R.string.edit_task);
            binding.buttonDeleteTask.setVisibility(android.view.View.VISIBLE);
            io.execute(() -> {
                Task t = db.taskDao().getTaskById(taskId);
                if (t != null) {
                    runOnUiThread(() -> {
                        binding.inputTitle.setText(t.title);
                        binding.inputDescription.setText(t.description);
                        binding.inputNotes.setText(t.notes);
                        binding.inputAssignee.setText(t.assignee, false);
                        
                        binding.autoCompleteStatus.setText(statusLabels[statusToPosition(t.status)], false);
                        binding.autoCompletePriority.setText(priorityLabels[priorityToPosition(t.priority)], false);
                        
                        selectedDeadlineMillis = t.deadline;
                        refreshDeadlinePreview();
                    });
                }
            });
        } else {
            binding.toolbar.setTitle(R.string.add_task);
            binding.autoCompleteStatus.setText(statusLabels[0], false);
        }

        binding.buttonPickDate.setOnClickListener(v -> openDatePicker());
        binding.buttonClearDeadline.setOnClickListener(v -> {
            selectedDeadlineMillis = null;
            refreshDeadlinePreview();
        });

        binding.buttonSave.setOnClickListener(v -> saveTask());
        binding.buttonDeleteTask.setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private static int priorityToPosition(TaskPriority p) {
        if (p == TaskPriority.HIGH) {
            return 2;
        }
        if (p == TaskPriority.LOW) {
            return 0;
        }
        return 1;
    }

    private TaskPriority labelToPriority(String label) {
        if (label.equals(priorityLabels[2])) {
            return TaskPriority.HIGH;
        }
        if (label.equals(priorityLabels[0])) {
            return TaskPriority.LOW;
        }
        return TaskPriority.MEDIUM;
    }

    private void openDatePicker() {
        Long initial = selectedDeadlineMillis != null
                ? utcMidnightFromLocalDay(selectedDeadlineMillis)
                : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.deadline))
                .setSelection(initial)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            selectedDeadlineMillis = localDayStartFromPickerUtc(selection);
            refreshDeadlinePreview();
        });
        picker.show(getSupportFragmentManager(), "deadline");
    }

    private static long localDayStartFromPickerUtc(long utcPickerMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcPickerMillis);
        int y = utc.get(Calendar.YEAR);
        int m = utc.get(Calendar.MONTH);
        int d = utc.get(Calendar.DAY_OF_MONTH);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(y, m, d, 0, 0, 0);
        return local.getTimeInMillis();
    }

    private static long utcMidnightFromLocalDay(long localDayStartMillis) {
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(localDayStartMillis);
        int y = local.get(Calendar.YEAR);
        int m = local.get(Calendar.MONTH);
        int d = local.get(Calendar.DAY_OF_MONTH);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(y, m, d, 0, 0, 0);
        return utc.getTimeInMillis();
    }

    private void refreshDeadlinePreview() {
        if (selectedDeadlineMillis == null) {
            binding.textDeadlinePreview.setText(R.string.no_deadline_set);
        } else {
            binding.textDeadlinePreview.setText(previewFmt.format(selectedDeadlineMillis));
        }
    }

    private static int statusToPosition(TaskStatus s) {
        if (s == TaskStatus.IN_PROGRESS) {
            return 1;
        }
        if (s == TaskStatus.COMPLETED) {
            return 2;
        }
        return 0;
    }

    private TaskStatus labelToStatus(String label) {
        if (label.equals(statusLabels[1])) {
            return TaskStatus.IN_PROGRESS;
        }
        if (label.equals(statusLabels[2])) {
            return TaskStatus.COMPLETED;
        }
        return TaskStatus.PENDING;
    }

    private void saveTask() {
        String title = binding.inputTitle.getText() != null
                ? binding.inputTitle.getText().toString().trim()
                : "";
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, R.string.error_task_title, Toast.LENGTH_SHORT).show();
            return;
        }
        String description = binding.inputDescription.getText() != null
                ? binding.inputDescription.getText().toString().trim()
                : "";
        String notes = binding.inputNotes.getText() != null
                ? binding.inputNotes.getText().toString().trim()
                : "";
        String assignee = binding.inputAssignee.getText() != null
                ? binding.inputAssignee.getText().toString().trim()
                : "";
        
        TaskStatus newStatus = labelToStatus(binding.autoCompleteStatus.getText().toString());
        TaskPriority priority = labelToPriority(binding.autoCompletePriority.getText().toString());

        io.execute(() -> {
            if (editingTaskId != null) {
                Task existing = db.taskDao().getTaskById(editingTaskId);
                if (existing != null) {
                    TaskStatus old = existing.status;
                    existing.title = title;
                    existing.description = description;
                    existing.notes = notes;
                    existing.assignee = assignee;
                    existing.deadline = selectedDeadlineMillis;
                    existing.status = newStatus;
                    existing.priority = priority;
                    EntityTimestamps.touch(existing);
                    db.taskDao().update(existing);
                    pushTaskToSupabase(existing, projectId);
                    ProjectCompletionHelper.refreshProjectCompletion(db, projectId);
                    if (newStatus == TaskStatus.COMPLETED && old != TaskStatus.COMPLETED) {
                        StreakTracker.onTaskCompleted(AddTaskActivity.this);
                    }
                }
            } else {
                Task t = new Task();
                t.projectId = projectId;
                t.title = title;
                t.description = description;
                t.notes = notes;
                t.assignee = assignee;
                t.deadline = selectedDeadlineMillis;
                t.status = newStatus;
                t.priority = priority;
                EntityTimestamps.touch(t);
                long taskId = db.taskDao().insert(t);
                t.id = taskId;
                pushTaskToSupabase(t, projectId);
                ProjectCompletionHelper.refreshProjectCompletion(db, projectId);
                if (newStatus == TaskStatus.COMPLETED) {
                    StreakTracker.onTaskCompleted(AddTaskActivity.this);
                }
            }
            runOnUiThread(this::finish);
        });
    }

    private void pushTaskToSupabase(Task task, long localProjectId) {
        io.execute(() -> {
            Project p = db.projectDao().getProjectById(localProjectId);
            if (p == null || p.remoteId == null) return;

            SupabaseService.TaskSync sync = new SupabaseService.TaskSync(
                    task.remoteId,
                    p.remoteId,
                    task.title,
                    task.description,
                    task.assignee,
                    task.status.name(),
                    task.priority.name(),
                    task.deadline,
                    task.lastModified
            );

            SupabaseService.upsertTask(sync, new SupabaseCallback<Long>() {
                @Override
                public void onSuccess(Long remoteId) {
                    if (task.remoteId == null) {
                        task.remoteId = remoteId;
                        io.execute(() -> db.taskDao().update(task));
                    }
                }

                @Override
                public void onError(Throwable error) {
                }
            });
        });
    }

    private void confirmDelete() {
        if (editingTaskId == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_task_title)
                .setMessage(R.string.delete_task_message)
                .setPositiveButton(R.string.delete, (d, w) -> io.execute(() -> {
                    Task t = db.taskDao().getTaskById(editingTaskId);
                    if (t != null) {
                        if (t.remoteId != null) {
                            SupabaseService.deleteTask(t.remoteId, new SupabaseCallback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(Throwable e) {}
                            });
                        }
                        db.taskDao().delete(t);
                        ProjectCompletionHelper.refreshProjectCompletion(db, projectId);
                    }
                    runOnUiThread(this::finish);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
