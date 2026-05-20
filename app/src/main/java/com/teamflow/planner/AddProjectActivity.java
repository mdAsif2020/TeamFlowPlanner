package com.teamflow.planner;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.databinding.ActivityAddProjectBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.util.EntityTimestamps;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Create or edit a project (online first, stored in Room and synced to Firestore).
 */
public class AddProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private ActivityAddProjectBinding binding;
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Long editingId;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);
        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        long id = getIntent().getLongExtra(EXTRA_PROJECT_ID, -1L);
        if (id > 0) {
            editingId = id;
            binding.toolbar.setTitle(R.string.edit_project);
            io.execute(() -> {
                Project p = db.projectDao().getProjectById(id);
                if (p != null) {
                    runOnUiThread(() -> {
                        binding.inputName.setText(p.name);
                        binding.inputDescription.setText(p.description);
                    });
                }
            });
        } else {
            binding.toolbar.setTitle(R.string.add_project);
        }

        binding.buttonSave.setOnClickListener(v -> saveProject());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void saveProject() {
        String name = binding.inputName.getText() != null
                ? binding.inputName.getText().toString().trim()
                : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.error_project_name, Toast.LENGTH_SHORT).show();
            return;
        }
        String desc = binding.inputDescription.getText() != null
                ? binding.inputDescription.getText().toString().trim()
                : "";

        io.execute(() -> {
            String email = sessionManager.getUserEmail();
            if (email == null || email.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "Error: User email not found. Please log in again.", Toast.LENGTH_LONG).show());
                return;
            }

            if (editingId != null) {
                Project existing = db.projectDao().getProjectById(editingId);
                if (existing != null) {
                    existing.name = name;
                    existing.description = desc;
                    existing.ownerEmail = email; // Ensure email is set
                    EntityTimestamps.touch(existing);
                    db.projectDao().update(existing);
                    syncProjectToSupabase(existing);
                }
            } else {
                Project p = new Project();
                p.name = name;
                p.description = desc;
                p.ownerEmail = email;
                p.isCompleted = false;
                long now = System.currentTimeMillis();
                p.createdAt = now;
                p.lastModified = now;
                long localId = db.projectDao().insert(p);
                p.id = localId;
                syncProjectToSupabase(p);
                runOnUiThread(() -> showInviteOptionDialog(p));
            }
        });
    }

    private void showInviteOptionDialog(Project project) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Project Created")
                .setMessage("Your project '" + project.name + "' has been created. Would you like to invite team members now?")
                .setPositiveButton("Invite Members", (dialog, which) -> showAddMemberDialog(project))
                .setNegativeButton("Maybe Later", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showAddMemberDialog(Project project) {
        TextInputLayout textInputLayout = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
        textInputLayout.setHint("Username or Email");
        textInputLayout.setBoxCornerRadii(48f, 48f, 48f, 48f);

        AutoCompleteTextView input = new AutoCompleteTextView(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setThreshold(1);

        textInputLayout.addView(input);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        params.setMargins(margin, (int) (8 * getResources().getDisplayMetrics().density), margin, 0);
        container.setLayoutParams(params);
        container.addView(textInputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Invite Team Member")
                .setView(container)
                .setPositiveButton("Search", (d, w) -> {
                    String query = input.getText().toString().trim();
                    if (TextUtils.isEmpty(query)) {
                        Toast.makeText(this, "Please enter a username or email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    searchAndInvite(project, query);
                })
                .setNegativeButton("Done", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void searchAndInvite(Project project, String query) {
        SupabaseService.searchProfiles(query, new SupabaseCallback<List<SupabaseService.Profile>>() {
            @Override
            public void onSuccess(List<SupabaseService.Profile> results) {
                if (results == null || results.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddProjectActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        showAddMemberDialog(project); // Show dialog again
                    });
                    return;
                }

                SupabaseService.Profile target = results.get(0);
                for (SupabaseService.Profile p : results) {
                    if (query.equalsIgnoreCase(p.getUsername()) || query.equalsIgnoreCase(p.getEmail())) {
                        target = p;
                        break;
                    }
                }

                SupabaseService.Profile finalTarget = target;
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(AddProjectActivity.this)
                            .setTitle("Send Invitation")
                            .setMessage("Invite " + finalTarget.getName() + " (@" + finalTarget.getUsername() + ") to '" + project.name + "'?")
                            .setPositiveButton("Send", (d, w) -> sendActualInvitation(project, finalTarget))
                            .setNegativeButton("Back", (d, w) -> showAddMemberDialog(project))
                            .show();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    Toast.makeText(AddProjectActivity.this, "Search failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    showAddMemberDialog(project);
                });
            }
        });
    }

    private void sendActualInvitation(Project project, SupabaseService.Profile target) {
        io.execute(() -> {
            if (project.remoteId == null) {
                runOnUiThread(() -> {
                    Toast.makeText(AddProjectActivity.this, "Project sync in progress, please wait...", Toast.LENGTH_SHORT).show();
                    // Retry after a short delay if needed, or just ask them to wait
                    showAddMemberDialog(project);
                });
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
                    runOnUiThread(() -> {
                        Toast.makeText(AddProjectActivity.this, "Invitation sent to " + target.getUsername(), Toast.LENGTH_SHORT).show();
                        showAddMemberDialog(project); // Allow inviting more
                    });
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddProjectActivity.this, "Failed to send invitation", Toast.LENGTH_SHORT).show();
                        showAddMemberDialog(project);
                    });
                }
            });
        });
    }

    private void syncProjectToSupabase(Project p) {
        android.util.Log.d("AddProjectActivity", "Syncing project to Supabase: " + p.name + " (local ID: " + p.id + ")");
        SupabaseService.ProjectSync sync = new SupabaseService.ProjectSync(
                p.remoteId,
                p.name,
                p.description,
                p.ownerEmail,
                p.isCompleted,
                p.isPinned,
                p.createdAt,
                p.lastModified
        );

        SupabaseService.upsertProject(sync, new SupabaseCallback<Long>() {
            @Override
            public void onSuccess(Long remoteId) {
                android.util.Log.d("AddProjectActivity", "Project synced successfully. Remote ID: " + remoteId);
                p.remoteId = remoteId;
                io.execute(() -> {
                    db.projectDao().update(p);
                    runOnUiThread(() -> Toast.makeText(AddProjectActivity.this, "Synced to cloud!", Toast.LENGTH_SHORT).show());
                });
            }

            @Override
            public void onError(Throwable error) {
                android.util.Log.e("AddProjectActivity", "Sync failed: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    String msg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    Toast.makeText(AddProjectActivity.this, "Sync failed: " + msg, Toast.LENGTH_LONG).show();
                    // Log the error stack trace for debugging
                    error.printStackTrace();
                });
            }
        });
    }
}
