package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.User;
import com.teamflow.planner.databinding.ActivityMemberDirectoryBinding;
import com.teamflow.planner.ui.adapter.SimpleNameAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Lists registered users from the local database so you can view profiles and tasks.
 */
public class MemberDirectoryActivity extends AppCompatActivity {

    private ActivityMemberDirectoryBinding binding;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberDirectoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = AppDatabase.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        SimpleNameAdapter adapter = new SimpleNameAdapter(new SimpleNameAdapter.Listener() {
            @Override
            public void onNameClick(@NonNull String name) {
                openProfile(name);
            }

            @Override
            public void onMoreClick(@NonNull View anchor, @NonNull String name) {
                showPopupMenu(anchor, name);
            }
        });
        binding.recyclerNames.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNames.setAdapter(adapter);

        // Fetch all registered users from the local DB
        Executors.newSingleThreadExecutor().execute(() -> {
            List<User> users = db.userDao().getAllUsers();
            runOnUiThread(() -> {
                adapter.submitList(users);
                binding.textEmptyNames.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showPopupMenu(View anchor, String name) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_member_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_view_profile) {
                openProfile(name);
                return true;
            } else if (id == R.id.action_delete) {
                confirmDelete(name);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void openProfile(String name) {
        Intent i = new Intent(this, MemberProfileActivity.class);
        i.putExtra(MemberProfileActivity.EXTRA_MEMBER_NAME, name);
        startActivity(i);
    }

    private void confirmDelete(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Member?")
                .setMessage("This will unassign '" + name + "' from all tasks. This cannot be undone.")
                .setPositiveButton("Remove", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.taskDao().unassignPerson(name);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
