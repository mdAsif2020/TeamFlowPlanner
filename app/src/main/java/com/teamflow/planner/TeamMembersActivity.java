package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.data.entity.User;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.databinding.ActivityTeamMembersBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.TeamMemberRosterAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manage name-based team members for a project (assignee suggestions + collaboration roster).
 * Now supports suggestions from registered users when adding a member.
 */
public class TeamMembersActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private ActivityTeamMembersBinding binding;
    private AppDatabase db;
    private long projectId;
    private TeamMemberRosterAdapter adapter;
    private SessionManager sessionManager;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<String> registeredUserNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTeamMembersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectId = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1L);
        if (projectId <= 0) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        adapter = new TeamMemberRosterAdapter(new TeamMemberRosterAdapter.Listener() {
            @Override
            public void onViewTasks(@NonNull TeamMember member) {
                Intent i = new Intent(TeamMembersActivity.this, MemberTasksActivity.class);
                i.putExtra(MemberTasksActivity.EXTRA_ASSIGNEE_NAME, member.name);
                startActivity(i);
            }

            @Override
            public void onRemove(@NonNull TeamMember member) {
                io.execute(() -> db.teamMemberDao().delete(member));
            }
        });
        binding.recyclerMembers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMembers.setAdapter(adapter);

        db.teamMemberDao().observeMembersForProject(projectId).observe(this, members -> {
            adapter.submitList(members);
            boolean empty = members == null || members.isEmpty();
            binding.textEmptyMembers.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        // Load registered users for suggestions
        loadRegisteredUsers();
        syncMembersFromSupabase();

        binding.fabAddMember.setOnClickListener(v -> showAddMemberDialog());
    }

    private void syncMembersFromSupabase() {
        io.execute(() -> {
            Project p = db.projectDao().getProjectById(projectId);
            if (p != null && p.remoteId != null) {
                SupabaseService.fetchProjectMembers(p.remoteId, new SupabaseCallback<List<SupabaseService.ProjectMemberSync>>() {
                    @Override
                    public void onSuccess(List<SupabaseService.ProjectMemberSync> members) {
                        io.execute(() -> {
                            List<TeamMember> localMembers = db.teamMemberDao().getMembersForProjectSync(projectId);
                            for (SupabaseService.ProjectMemberSync ms : members) {
                                boolean exists = false;
                                for (TeamMember tm : localMembers) {
                                    if (tm.name.equalsIgnoreCase(ms.getUser_name())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    TeamMember nm = new TeamMember();
                                    nm.projectId = projectId;
                                    nm.name = ms.getUser_name();
                                    nm.username = ms.getUser_username();
                                    if (nm.username == null) nm.username = ms.getUser_name();
                                    nm.createdAt = System.currentTimeMillis();
                                    db.teamMemberDao().insert(nm);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        runOnUiThread(() -> {
                            Toast.makeText(TeamMembersActivity.this, "Failed to sync members: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
    }

    private void loadRegisteredUsers() {
        io.execute(() -> {
            List<User> users = db.userDao().getAllUsers();
            registeredUserNames.clear();
            for (User u : users) {
                registeredUserNames.add(u.name);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void showAddMemberDialog() {
        TextInputLayout textInputLayout = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
        textInputLayout.setHint("Username or Email");
        textInputLayout.setBoxCornerRadii(48f, 48f, 48f, 48f);

        AutoCompleteTextView input = new AutoCompleteTextView(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setThreshold(1);
        
        ArrayAdapter<String> suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, registeredUserNames);
        input.setAdapter(suggestionAdapter);

        textInputLayout.addView(input);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, (int) (8 * getResources().getDisplayMetrics().density), margin, 0);
        container.setLayoutParams(params);
        container.addView(textInputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_team_member)
                .setView(container)
                .setPositiveButton("Invite", (d, w) -> {
                    String query = input.getText().toString().trim();
                    if (TextUtils.isEmpty(query)) {
                        Toast.makeText(this, "Please enter a username or email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    searchAndInvite(query);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void searchAndInvite(String query) {
        SupabaseService.searchProfiles(query, new SupabaseCallback<List<SupabaseService.Profile>>() {
            @Override
            public void onSuccess(List<SupabaseService.Profile> results) {
                if (results == null || results.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(TeamMembersActivity.this, "User not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                // If multiple, maybe find exact username match
                SupabaseService.Profile target = results.get(0);
                for (SupabaseService.Profile p : results) {
                    if (query.equalsIgnoreCase(p.getUsername()) || query.equalsIgnoreCase(p.getEmail())) {
                        target = p;
                        break;
                    }
                }

                SupabaseService.Profile finalTarget = target;
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(TeamMembersActivity.this)
                            .setTitle("Send Invitation")
                            .setMessage("Invite " + finalTarget.getName() + " (@" + finalTarget.getUsername() + ") to this project?")
                            .setPositiveButton("Send", (d, w) -> sendActualInvitation(finalTarget))
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(TeamMembersActivity.this, "Search failed: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendActualInvitation(SupabaseService.Profile target) {
        io.execute(() -> {
            Project project = db.projectDao().getProjectById(projectId);
            if (project == null || project.remoteId == null) {
                runOnUiThread(() -> Toast.makeText(TeamMembersActivity.this, "Project not synced to cloud yet", Toast.LENGTH_SHORT).show());
                return;
            }

            SupabaseService.Invitation inv = new SupabaseService.Invitation(
                    null,
                    project.remoteId,
                    project.name,
                    sessionManager.getUserEmail(),
                    sessionManager.getUserUsername(),
                    target.getEmail(),
                    "PENDING"
            );

            SupabaseService.sendInvitation(inv, new SupabaseCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> Toast.makeText(TeamMembersActivity.this, "Invitation sent to " + target.getUsername(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> Toast.makeText(TeamMembersActivity.this, "Failed to send invitation", Toast.LENGTH_SHORT).show());
                }
            });
        });
    }

}
