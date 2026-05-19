package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.DailyFocusItem;
import com.teamflow.planner.data.TaskPriority;
import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Notification;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.data.ProjectListItem;
import com.teamflow.planner.databinding.ActivityDashboardBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.databinding.ItemDailyFocusBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.ProjectAdapter;
import com.teamflow.planner.util.NightModeHelper;
import com.teamflow.planner.util.StreakTracker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home dashboard: stats, streak, Smart Daily Focus, project list, search, theme.
 */
public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    private AppDatabase db;
    private ProjectAdapter projectAdapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat focusDateFmt = new SimpleDateFormat("MMM d", Locale.getDefault());

    private int teamTotalTasks;
    private int teamCompletedTasks;

    private int myTotalTasks;
    private int myCompletedTasks;

    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // Click on "TeamFlow" title to show developer details
        for (int i = 0; i < binding.toolbar.getChildCount(); i++) {
            View view = binding.toolbar.getChildAt(i);
            if (view instanceof TextView) {
                TextView tv = (TextView) view;
                if (tv.getText().equals(getString(R.string.app_name))) {
                    tv.setOnClickListener(v -> showDeveloperDetails());
                    break;
                }
            }
        }

        String currentUserName = sessionManager.getUserName();
        binding.textGreeting.setText("Hi, " + currentUserName + "!");
        updateProfileDisplay(currentUserName);

        binding.buttonProfile.setOnClickListener(v -> {
            Intent i = new Intent(this, MemberProfileActivity.class);
            i.putExtra(MemberProfileActivity.EXTRA_MEMBER_NAME, sessionManager.getUserName());
            i.putExtra(MemberProfileActivity.EXTRA_MEMBER_EMAIL, sessionManager.getUserEmail());
            i.putExtra(MemberProfileActivity.EXTRA_MEMBER_USERNAME, sessionManager.getUserUsername());
            startActivity(i);
        });

        binding.buttonNotifications.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });

        db = AppDatabase.getInstance(this);

        // Observe local unread notifications
        db.notificationDao().observeUnreadCount().observe(this, count -> {
            updateNotificationBadge(count != null && count > 0);
        });

        // Periodically check for new invitations to show badge
        io.execute(this::startInvitationRealtimeListener);

        projectAdapter = new ProjectAdapter(new ProjectAdapter.Listener() {
            @Override
            public void onProjectClick(@NonNull Project project) {
                Intent i = new Intent(DashboardActivity.this, ProjectActivity.class);
                i.putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.id);
                startActivity(i);
            }

            @Override
            public void onProjectOverflow(@NonNull Project project, View anchor) {
                PopupMenu menu = new PopupMenu(DashboardActivity.this, anchor);
                menu.getMenuInflater().inflate(R.menu.menu_project_popup, menu.getMenu());
                menu.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.action_edit_project) {
                        Intent ed = new Intent(DashboardActivity.this, AddProjectActivity.class);
                        ed.putExtra(AddProjectActivity.EXTRA_PROJECT_ID, project.id);
                        startActivity(ed);
                        return true;
                    }
                    if (id == R.id.action_invite_member) {
                        Intent i = new Intent(DashboardActivity.this, TeamMembersActivity.class);
                        i.putExtra(TeamMembersActivity.EXTRA_PROJECT_ID, project.id);
                        startActivity(i);
                        return true;
                    }
                    if (id == R.id.action_delete_project) {
                        confirmDeleteProject(project);
                        return true;
                    }
                    return false;
                });
                menu.show();
            }
        });

        binding.recyclerProjects.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProjects.setAdapter(projectAdapter);
        binding.recyclerProjects.setNestedScrollingEnabled(false);

        binding.fabAddProject.setOnClickListener(v ->
                startActivity(new Intent(this, AddProjectActivity.class)));

        db.projectDao().observeProjectsWithStats().observe(this, rows -> {
            projectAdapter.submitSource(rows);
            boolean empty = rows == null || rows.isEmpty();
            binding.textNoProjects.setVisibility(empty ? View.VISIBLE : View.GONE);
            
            if (rows != null && !rows.isEmpty()) {
                startGlobalRealtimeListeners(rows);
            }
        });

        // Team Progress Observers
        db.taskDao().observeTotalTaskCount().observe(this, total -> {
            teamTotalTasks = total == null ? 0 : total;
            applyTeamProgress();
        });

        db.taskDao().observeCompletedTaskCount().observe(this, done -> {
            teamCompletedTasks = done == null ? 0 : done;
            applyTeamProgress();
        });

        // Individual Progress Observers
        db.taskDao().observeTasksForAssignee(currentUserName).observe(this, tasks -> {
            if (tasks != null) {
                myTotalTasks = tasks.size();
                myCompletedTasks = 0;
                for (var t : tasks) {
                    if (t.task.status == com.teamflow.planner.data.TaskStatus.COMPLETED) {
                        myCompletedTasks++;
                    }
                }
                applyIndividualProgress();
            }
        });

        db.taskDao().observeDailyFocus().observe(this, this::bindDailyFocus);

        setupBottomNavigation();
        setupDashboardInteractions();
        loadProfilePicture();
        refreshStreakUi();
    }

    private void startGlobalRealtimeListeners(List<ProjectListItem> projects) {
        for (var p : projects) {
            if (p.project.remoteId != null) {
                long remoteId = p.project.remoteId;
                SupabaseService.observeTasks(remoteId, new SupabaseCallback<List<SupabaseService.TaskSync>>() {
                    @Override
                    public void onSuccess(List<SupabaseService.TaskSync> tasks) {
                        // Check for new tasks or status changes
                        io.execute(() -> processTaskUpdates(p.project, tasks));
                    }
                    @Override
                    public void onError(Throwable error) {}
                });
            }
        }
    }

    private void processTaskUpdates(Project project, List<SupabaseService.TaskSync> remoteTasks) {
        for (var rt : remoteTasks) {
            if (rt.getId() == null) continue;
            
            Task local = db.taskDao().getTaskByRemoteIdSync(rt.getId());
            String eventId;
            if (local == null) {
                eventId = "new_task_" + rt.getId();
                if (db.notificationDao().exists(eventId)) continue;

                // New task!
                Notification n = new Notification(
                    "New Task in " + project.name,
                    "Task '" + rt.getTitle() + "' was added.",
                    "TASK_UPDATE"
                );
                n.projectId = project.id;
                n.remoteId = eventId;
                db.notificationDao().insert(n);
            } else if (local.status != TaskStatus.valueOf(rt.getStatus())) {
                eventId = "status_change_" + rt.getId() + "_" + rt.getStatus();
                if (db.notificationDao().exists(eventId)) continue;

                // Status changed!
                Notification n = new Notification(
                    "Task Update in " + project.name,
                    "Task '" + rt.getTitle() + "' is now " + rt.getStatus() + ".",
                    "TASK_UPDATE"
                );
                n.projectId = project.id;
                n.remoteId = eventId;
                db.notificationDao().insert(n);
            }
        }
    }

    private void startInvitationRealtimeListener() {
        String email = sessionManager.getUserEmail();
        if (email == null) return;
        SupabaseService.observeInvitations(email, new SupabaseCallback<List<SupabaseService.Invitation>>() {
            @Override
            public void onSuccess(List<SupabaseService.Invitation> invitations) {
                if (!invitations.isEmpty()) {
                    runOnUiThread(() -> updateNotificationBadge(true));
                }
            }
            @Override
            public void onError(Throwable error) {}
        });
    }

    private void updateNotificationBadge(boolean show) {
        binding.notificationBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateProfileDisplay(String name) {
        if (name != null && !name.isEmpty()) {
            binding.textProfileLetter.setText(String.valueOf(name.charAt(0)).toUpperCase());
        } else {
            binding.textProfileLetter.setText("?");
        }
    }

    private void loadProfilePicture() {
        String currentName = sessionManager.getUserName();
        String currentEmail = sessionManager.getUserEmail();
        binding.textGreeting.setText("Hi, " + currentName + "!");
        updateProfileDisplay(currentName);

        String cachedPhotoUrl = sessionManager.getUserPhotoUrl();
        if (cachedPhotoUrl != null) {
            applyProfileImage(cachedPhotoUrl);
        }

        SupabaseCallback<SupabaseService.Profile> callback = new SupabaseCallback<>() {
            @Override
            public void onSuccess(SupabaseService.Profile profile) {
                runOnUiThread(() -> {
                    if (profile.getName() != null) {
                        sessionManager.updateUserName(profile.getName());
                        binding.textGreeting.setText("Hi, " + profile.getName() + "!");
                        updateProfileDisplay(profile.getName());
                    }
                    if (profile.getPhoto_url() != null) {
                        sessionManager.updateUserPhotoUrl(profile.getPhoto_url());
                        applyProfileImage(profile.getPhoto_url());
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                // Ignore
            }
        };

        if (sessionManager.getUserId() != null) {
            SupabaseService.fetchProfileById(sessionManager.getUserId(), callback);
        } else if (currentEmail != null) {
            SupabaseService.fetchProfileByEmail(currentEmail, callback);
        } else {
            SupabaseService.fetchProfile(currentName, callback);
        }
    }

    private void applyProfileImage(String url) {
        if (url == null || url.isEmpty()) return;
        
        binding.textProfileLetter.setVisibility(View.GONE);
        binding.imageProfileToolbar.setVisibility(View.VISIBLE);
        
        // Use a signature that changes daily to allow some caching but ensure updates
        String daySignature = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
        
        Glide.with(this)
                .load(url)
                .signature(new com.bumptech.glide.signature.ObjectKey(daySignature))
                .circleCrop()
                .into(binding.imageProfileToolbar);
    }

    private void setupBottomNavigation() {
        binding.btnNavHome.setOnClickListener(v -> {
            binding.nestedScrollView.smoothScrollTo(0, 0);
        });

        binding.btnNavMembers.setOnClickListener(v -> {
            startActivity(new Intent(this, MemberDirectoryActivity.class));
        });

        binding.btnNavWorkload.setOnClickListener(v -> {
            Intent intent = new Intent(this, MemberTasksActivity.class);
            intent.putExtra(MemberTasksActivity.EXTRA_ASSIGNEE_NAME, sessionManager.getUserName());
            startActivity(intent);
        });

        binding.btnNavProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, MemberProfileActivity.class);
            intent.putExtra(MemberProfileActivity.EXTRA_MEMBER_NAME, sessionManager.getUserName());
            intent.putExtra(MemberProfileActivity.EXTRA_MEMBER_EMAIL, sessionManager.getUserEmail());
            intent.putExtra(MemberProfileActivity.EXTRA_MEMBER_USERNAME, sessionManager.getUserUsername());
            startActivity(intent);
        });
    }

    private void setupDashboardInteractions() {
        binding.btnSeeAllProjects.setOnClickListener(v -> {
            // Smooth scroll to projects section
            binding.nestedScrollView.smoothScrollTo(0, binding.recyclerProjects.getTop());
        });
    }

    private void showDeveloperDetails() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About Developer")
                .setMessage("Developer - Modabbir Asif | CSE | BAUST")
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search_projects);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_projects));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    projectAdapter.setSearchQuery(newText);
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_invitations) {
            startActivity(new Intent(this, InvitationsActivity.class));
            return true;
        }
        if (id == R.id.action_sync_now) {
            // Placeholder for Supabase sync if needed
            Toast.makeText(this, "Syncing with Supabase...", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_member_workload) {
            startActivity(new Intent(this, MemberDirectoryActivity.class));
            return true;
        }
        if (id == R.id.action_theme_system) {
            NightModeHelper.saveAndApply(this, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            return true;
        }
        if (id == R.id.action_theme_light) {
            NightModeHelper.saveAndApply(this, AppCompatDelegate.MODE_NIGHT_NO);
            return true;
        }
        if (id == R.id.action_theme_dark) {
            NightModeHelper.saveAndApply(this, AppCompatDelegate.MODE_NIGHT_YES);
            return true;
        }
        if (id == R.id.action_logout) {
            sessionManager.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyTeamProgress() {
        int pct = teamTotalTasks == 0 ? 0 : Math.round((teamCompletedTasks * 100f) / teamTotalTasks);
        binding.progressTeam.setProgress(pct, true);
        binding.textTeamProgressPct.setText(pct + "%");
        binding.textTeamTaskCount.setText(teamCompletedTasks + " / " + teamTotalTasks + " Done");
    }

    private void applyIndividualProgress() {
        int pct = myTotalTasks == 0 ? 0 : Math.round((myCompletedTasks * 100f) / myTotalTasks);
        binding.progressIndividual.setProgress(pct, true);
        binding.textIndividualProgressPct.setText(pct + "%");
        binding.textIndividualTaskCount.setText(myCompletedTasks + " / " + myTotalTasks + " Done");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfilePicture();
        refreshStreakUi();
        syncAllData();
    }

    private void syncAllData() {
        String email = sessionManager.getUserEmail();
        if (email == null || email.isEmpty()) return;

        SupabaseService.fetchMyProjects(email, new SupabaseCallback<List<SupabaseService.ProjectSync>>() {
            @Override
            public void onSuccess(List<SupabaseService.ProjectSync> projects) {
                io.execute(() -> {
                    for (SupabaseService.ProjectSync ps : projects) {
                        Project p = db.projectDao().getProjectByRemoteIdSync(ps.getId());
                        if (p == null) {
                            p = new Project();
                            p.remoteId = ps.getId();
                            p.createdAt = ps.getCreated_at() != null ? ps.getCreated_at() : System.currentTimeMillis();
                        }
                        p.name = ps.getName();
                        p.description = ps.getDescription();
                        p.ownerEmail = ps.getOwner_email();
                        p.lastModified = System.currentTimeMillis();
                        db.projectDao().insertOrReplace(p);
                        
                        Project localP = db.projectDao().getProjectByRemoteIdSync(ps.getId());
                        if (localP != null) {
                            syncTasksForProject(localP.id, ps.getId());
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable error) {}
        });
    }

    private void syncTasksForProject(long localProjectId, long remoteProjectId) {
        SupabaseService.fetchTasks(remoteProjectId, new SupabaseCallback<List<SupabaseService.TaskSync>>() {
            @Override
            public void onSuccess(List<SupabaseService.TaskSync> tasks) {
                io.execute(() -> {
                    for (SupabaseService.TaskSync ts : tasks) {
                        Task t = db.taskDao().getTaskByRemoteIdSync(ts.getId());
                        if (t == null) {
                            t = new Task();
                            t.remoteId = ts.getId();
                            t.projectId = localProjectId;
                        }
                        t.title = ts.getTitle();
                        t.description = ts.getDescription();
                        t.assignee = ts.getAssignee();
                        t.status = TaskStatus.valueOf(ts.getStatus());
                        t.priority = TaskPriority.valueOf(ts.getPriority());
                        t.deadline = ts.getDeadline();
                        t.lastModified = ts.getUpdated_at() != null ? ts.getUpdated_at() : System.currentTimeMillis();
                        db.taskDao().insertOrReplace(t);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void bindDailyFocus(List<DailyFocusItem> items) {
        binding.dailyFocusContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            binding.textDailyFocusEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.textDailyFocusEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (DailyFocusItem row : items) {
            ItemDailyFocusBinding card = ItemDailyFocusBinding.inflate(inflater, binding.dailyFocusContainer, false);
            card.textProject.setText(getString(R.string.focus_project_label, row.projectName));
            card.textTask.setText(row.task.title);
            String assignee = row.task.assignee == null || row.task.assignee.isEmpty()
                    ? getString(R.string.unassigned)
                    : row.task.assignee;
            String datePart = row.task.deadline == null
                    ? "—"
                    : focusDateFmt.format(new Date(row.task.deadline));
            card.textMeta.setText(assignee + " · " + datePart);
            card.getRoot().setOnClickListener(v -> {
                Intent i = new Intent(DashboardActivity.this, ProjectActivity.class);
                i.putExtra(ProjectActivity.EXTRA_PROJECT_ID, row.task.projectId);
                startActivity(i);
            });
            binding.dailyFocusContainer.addView(card.getRoot());
        }
    }

    private void refreshStreakUi() {
        int streak = StreakTracker.getCurrentStreak(this);
        binding.textStreak.setText(getString(R.string.streak_days, streak));
    }

    private void confirmDeleteProject(@NonNull Project project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_project_title)
                .setMessage(R.string.delete_project_message)
                .setPositiveButton(R.string.delete, (d, w) -> io.execute(() -> {
                    db.projectDao().delete(project);
                    if (project.remoteId != null) {
                        SupabaseService.deleteProject(project.remoteId, new SupabaseCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {}
                            @Override
                            public void onError(Throwable error) {}
                        });
                    }
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
