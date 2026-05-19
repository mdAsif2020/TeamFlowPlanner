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
import com.teamflow.planner.databinding.ActivityTeamMembersBinding;
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

        binding.fabAddMember.setOnClickListener(v -> showAddMemberDialog());
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
        // Create modern OutlinedBox TextInputLayout
        TextInputLayout textInputLayout = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
        textInputLayout.setHint(R.string.member_name_hint);
        textInputLayout.setBoxCornerRadii(48f, 48f, 48f, 48f);
        textInputLayout.setPadding(0, 0, 0, 0);

        AutoCompleteTextView input = new AutoCompleteTextView(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setThreshold(1); // Show suggestions after 1 character
        
        ArrayAdapter<String> suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, registeredUserNames);
        input.setAdapter(suggestionAdapter);

        textInputLayout.addView(input);

        // Add margins to the container
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, (int) (8 * getResources().getDisplayMetrics().density), margin, 0);
        container.setLayoutParams(params);
        container.addView(textInputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_team_member)
                .setView(container)
                .setPositiveButton(R.string.save, (d, w) -> {
                    CharSequence cs = input.getText();
                    String name = cs != null ? cs.toString().trim() : "";
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, R.string.error_member_name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    io.execute(() -> {
                        TeamMember m = new TeamMember();
                        m.projectId = projectId;
                        m.name = name;
                        m.createdAt = System.currentTimeMillis();
                        db.teamMemberDao().insert(m);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
