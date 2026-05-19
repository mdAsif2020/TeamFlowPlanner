package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.databinding.ActivityMemberTasksBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.MemberTasksAdapter;

import java.util.Locale;

/**
 * Shared task list for one assignee name across all projects.
 */
public class MemberTasksActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNEE_NAME = "extra_assignee_name";

    private ActivityMemberTasksBinding binding;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String name = getIntent().getStringExtra(EXTRA_ASSIGNEE_NAME);
        if (TextUtils.isEmpty(name)) {
            finish();
            return;
        }

        db = AppDatabase.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(""); // Hide title to use custom header
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Populate Header
        binding.textMemberNameHeader.setText(name);
        if (!name.isEmpty()) {
            binding.textAvatarLetter.setText(String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
        }

        loadMemberProfileImage(name);

        MemberTasksAdapter adapter = new MemberTasksAdapter(row -> {
            Intent i = new Intent(this, AddTaskActivity.class);
            i.putExtra(AddTaskActivity.EXTRA_PROJECT_ID, row.task.projectId);
            i.putExtra(AddTaskActivity.EXTRA_TASK_ID, row.task.id);
            startActivity(i);
        });
        binding.recyclerMemberTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMemberTasks.setAdapter(adapter);

        db.taskDao().observeTasksForAssignee(name).observe(this, tasks -> {
            adapter.submitList(tasks);
            int count = tasks == null ? 0 : tasks.size();
            binding.textTaskCountSub.setText(String.format(Locale.getDefault(), "%d Active Tasks", count));
            binding.textNoTasks.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        });
    }

    private void loadMemberProfileImage(String name) {
        SupabaseService.fetchProfile(name, new SupabaseCallback<>() {
            @Override
            public void onSuccess(SupabaseService.Profile profile) {
                if (profile.getPhoto_url() != null && !profile.getPhoto_url().isEmpty()) {
                    runOnUiThread(() -> {
                        binding.textAvatarLetter.setVisibility(View.GONE);
                        binding.imageProfile.setVisibility(View.VISIBLE);
                        Glide.with(MemberTasksActivity.this)
                                .load(profile.getPhoto_url())
                                .circleCrop()
                                .into(binding.imageProfile);
                    });
                }
            }

            @Override
            public void onError(Throwable error) {
                // Ignore
            }
        });
    }
}
