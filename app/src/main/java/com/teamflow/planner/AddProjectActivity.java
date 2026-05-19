package com.teamflow.planner;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.databinding.ActivityAddProjectBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.util.EntityTimestamps;

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
            if (editingId != null) {
                Project existing = db.projectDao().getProjectById(editingId);
                if (existing != null) {
                    existing.name = name;
                    existing.description = desc;
                    EntityTimestamps.touch(existing);
                    db.projectDao().update(existing);
                    syncProjectToSupabase(existing);
                }
            } else {
                Project p = new Project();
                p.name = name;
                p.description = desc;
                p.ownerEmail = sessionManager.getUserEmail(); // Set Owner
                p.isCompleted = false;
                long now = System.currentTimeMillis();
                p.createdAt = now;
                p.lastModified = now;
                long localId = db.projectDao().insert(p);
                p.id = localId;
                syncProjectToSupabase(p);
            }
            runOnUiThread(this::finish);
        });
    }

    private void syncProjectToSupabase(Project p) {
        SupabaseService.ProjectSync sync = new SupabaseService.ProjectSync(
                p.remoteId,
                p.name,
                p.description,
                p.ownerEmail,
                p.createdAt
        );

        SupabaseService.upsertProject(sync, new SupabaseCallback<Long>() {
            @Override
            public void onSuccess(Long remoteId) {
                p.remoteId = remoteId;
                db.projectDao().update(p);
            }

            @Override
            public void onError(Throwable error) {
                // Ignore sync errors for now, will retry later or manual sync
            }
        });
    }
}
