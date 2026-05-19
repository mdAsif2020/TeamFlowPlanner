package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.databinding.ActivityProjectDetailBinding;
import com.teamflow.planner.ui.TaskSwipeCallback;
import com.teamflow.planner.ui.adapter.TaskAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists tasks for one project: sort, swipe status, filters, search, team roster entry point.
 * Now includes a real-time chat feature for project members.
 */
public class ProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private static final Comparator<Task> DEADLINE_ASC = (a, b) -> {
        if (a.deadline == null && b.deadline == null) return 0;
        if (a.deadline == null) return 1;
        if (b.deadline == null) return -1;
        return Long.compare(a.deadline, b.deadline);
    };

    private static final Comparator<Task> DEADLINE_DESC = (a, b) -> -DEADLINE_ASC.compare(a, b);

    private ActivityProjectDetailBinding binding;
    private AppDatabase db;
    private long projectId;
    private Project currentProject;
    private TaskAdapter taskAdapter;
    private boolean sortDeadlineAscending = true;
    private final List<Task> lastSnapshot = new ArrayList<>();
    private final List<TeamMember> lastMembers = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    private TaskStatus statusFilter;
    @Nullable
    private String assigneeFilter;
    private String searchLower = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProjectDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1L);
        if (projectId <= 0) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        taskAdapter = new TaskAdapter(task -> {
            Intent i = new Intent(this, AddTaskActivity.class);
            i.putExtra(AddTaskActivity.EXTRA_PROJECT_ID, projectId);
            i.putExtra(AddTaskActivity.EXTRA_TASK_ID, task.id);
            startActivity(i);
        });

        binding.recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTasks.setAdapter(taskAdapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(
                new TaskSwipeCallback(taskAdapter, db, this, io, main));
        touchHelper.attachToRecyclerView(binding.recyclerTasks);

        binding.fabAddTask.setOnClickListener(v -> {
            Intent i = new Intent(this, AddTaskActivity.class);
            i.putExtra(AddTaskActivity.EXTRA_PROJECT_ID, projectId);
            startActivity(i);
        });

        binding.chipFilterAll.setOnClickListener(v -> {
            binding.chipGroupStatus.check(R.id.chipFilterAll);
            statusFilter = null;
            applyFiltersSortAndSubmit();
        });
        binding.chipFilterPending.setOnClickListener(v -> {
            binding.chipGroupStatus.check(R.id.chipFilterPending);
            statusFilter = TaskStatus.PENDING;
            applyFiltersSortAndSubmit();
        });
        binding.chipFilterProgress.setOnClickListener(v -> {
            binding.chipGroupStatus.check(R.id.chipFilterProgress);
            statusFilter = TaskStatus.IN_PROGRESS;
            applyFiltersSortAndSubmit();
        });
        binding.chipFilterDone.setOnClickListener(v -> {
            binding.chipGroupStatus.check(R.id.chipFilterDone);
            statusFilter = TaskStatus.COMPLETED;
            applyFiltersSortAndSubmit();
        });

        binding.dropdownAssigneeFilter.setOnItemClickListener((parent, view, position, id) -> {
            String value = (String) parent.getItemAtPosition(position);
            assigneeFilter = getString(R.string.filter_all).equals(value) ? null : value;
            applyFiltersSortAndSubmit();
        });

        db.projectDao().observeProject(projectId).observe(this, project -> {
            if (project != null) {
                currentProject = project;
                binding.textProjectName.setText(project.name);
                binding.textProjectDescription.setText(project.description);
            }
        });

        db.taskDao().observeTasksForProject(projectId).observe(this, tasks -> {
            lastSnapshot.clear();
            if (tasks != null) lastSnapshot.addAll(tasks);
            updateProgress();
            rebuildAssigneeDropdown();
            applyFiltersSortAndSubmit();
        });

        db.teamMemberDao().observeMembersForProject(projectId).observe(this, members -> {
            lastMembers.clear();
            if (members != null) lastMembers.addAll(members);
            binding.textMemberCount.setText(String.format(Locale.getDefault(), "%d Team Members", lastMembers.size()));
            rebuildAssigneeDropdown();
        });
    }

    private void updateProgress() {
        int total = lastSnapshot.size();
        int done = 0;
        for (Task t : lastSnapshot) {
            if (t.status == TaskStatus.COMPLETED) done++;
        }
        int pct = total == 0 ? 0 : (done * 100) / total;
        binding.progressProjectDetail.setProgress(pct, true);
        binding.textProjectProgressPct.setText(String.format(Locale.getDefault(), "%d%%", pct));
        binding.textTaskStats.setText(String.format(Locale.getDefault(), "%d / %d Tasks Done", done, total));
    }

    private void rebuildAssigneeDropdown() {
        LinkedHashSet<String> opts = new LinkedHashSet<>();
        opts.add(getString(R.string.filter_all));
        for (TeamMember m : lastMembers) if (m.name != null && !m.name.isEmpty()) opts.add(m.name);
        for (Task t : lastSnapshot) if (t.assignee != null && !t.assignee.isEmpty()) opts.add(t.assignee);
        List<String> list = new ArrayList<>(opts);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        binding.dropdownAssigneeFilter.setAdapter(ad);
        binding.dropdownAssigneeFilter.setText(assigneeFilter == null ? getString(R.string.filter_all) : assigneeFilter, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_project_detail, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search_tasks);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_tasks));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) { return false; }
                @Override
                public boolean onQueryTextChange(String newText) {
                    searchLower = newText == null ? "" : newText.trim().toLowerCase(Locale.getDefault());
                    applyFiltersSortAndSubmit();
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem sort = menu.findItem(R.id.action_sort_deadline);
        if (sort != null) {
            sort.setTitle(sortDeadlineAscending ? R.string.sort_deadline_asc : R.string.sort_deadline_desc);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_chat) {
            if (currentProject != null) {
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_PROJECT_ID, projectId);
                i.putExtra(ChatActivity.EXTRA_OWNER_EMAIL, currentProject.ownerEmail);
                startActivity(i);
            }
            return true;
        }
        if (id == R.id.action_sort_deadline) {
            sortDeadlineAscending = !sortDeadlineAscending;
            invalidateOptionsMenu();
            applyFiltersSortAndSubmit();
            return true;
        }
        if (id == R.id.action_team_members) {
            Intent i = new Intent(this, TeamMembersActivity.class);
            i.putExtra(TeamMembersActivity.EXTRA_PROJECT_ID, projectId);
            startActivity(i);
            return true;
        }
        if (id == R.id.action_edit_project) {
            Intent i = new Intent(this, AddProjectActivity.class);
            i.putExtra(AddProjectActivity.EXTRA_PROJECT_ID, projectId);
            startActivity(i);
            return true;
        }
        if (id == R.id.action_delete_project) {
            confirmDeleteProject();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void applyFiltersSortAndSubmit() {
        List<Task> copy = new ArrayList<>();
        for (Task t : lastSnapshot) {
            if (statusFilter != null && t.status != statusFilter) continue;
            if (assigneeFilter != null) {
                String a = t.assignee == null ? "" : t.assignee;
                if (!a.equalsIgnoreCase(assigneeFilter)) continue;
            }
            if (!searchLower.isEmpty()) {
                String title = t.title != null ? t.title.toLowerCase(Locale.getDefault()) : "";
                String desc = t.description != null ? t.description.toLowerCase(Locale.getDefault()) : "";
                if (!title.contains(searchLower) && !desc.contains(searchLower)) continue;
            }
            copy.add(t);
        }
        Collections.sort(copy, sortDeadlineAscending ? DEADLINE_ASC : DEADLINE_DESC);
        taskAdapter.submitList(copy);
        binding.textEmptyTasks.setVisibility(copy.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmDeleteProject() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_project_title)
                .setMessage(R.string.delete_project_message)
                .setPositiveButton(R.string.delete, (d, w) -> io.execute(() -> {
                    Project p = db.projectDao().getProjectById(projectId);
                    if (p != null) db.projectDao().delete(p);
                    main.post(this::finish);
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
