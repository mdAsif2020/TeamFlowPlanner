package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.User;
import com.teamflow.planner.databinding.ActivityMemberDirectoryBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.SimpleNameAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Lists registered users from Supabase so you can view profiles and invite them.
 */
public class MemberDirectoryActivity extends AppCompatActivity {

    private ActivityMemberDirectoryBinding binding;
    private AppDatabase db;
    private SimpleNameAdapter adapter;

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

        adapter = new SimpleNameAdapter(new SimpleNameAdapter.Listener() {
            @Override
            public void onNameClick(@NonNull SupabaseService.Profile profile) {
                openProfile(profile);
            }

            @Override
            public void onMoreClick(@NonNull View anchor, @NonNull SupabaseService.Profile profile) {
                showPopupMenu(anchor, profile);
            }
        });
        binding.recyclerNames.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNames.setAdapter(adapter);

        binding.editSearchNames.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchUsers(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Initially load some users (e.g. searching with empty string or common prefix)
        searchUsers("");
    }

    private void searchUsers(String query) {
        String currentEmail = new SessionManager(this).getUserEmail();
        SupabaseService.searchProfiles(query, new SupabaseCallback<List<SupabaseService.Profile>>() {
            @Override
            public void onSuccess(List<SupabaseService.Profile> profiles) {
                // Filter out current user from directory
                List<SupabaseService.Profile> filtered = new ArrayList<>();
                for (SupabaseService.Profile p : profiles) {
                    if (p.getEmail() != null && !p.getEmail().equalsIgnoreCase(currentEmail)) {
                        filtered.add(p);
                    }
                }
                runOnUiThread(() -> {
                    adapter.submitList(filtered);
                    binding.textEmptyNames.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> {
                    Toast.makeText(MemberDirectoryActivity.this, "Search failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.textEmptyNames.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showPopupMenu(View anchor, SupabaseService.Profile profile) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_member_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_view_profile) {
                openProfile(profile);
                return true;
            } else if (id == R.id.action_delete) {
                confirmDelete(profile.getName());
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void openProfile(SupabaseService.Profile profile) {
        Intent i = new Intent(this, MemberProfileActivity.class);
        i.putExtra(MemberProfileActivity.EXTRA_MEMBER_NAME, profile.getName());
        i.putExtra(MemberProfileActivity.EXTRA_MEMBER_EMAIL, profile.getEmail());
        i.putExtra(MemberProfileActivity.EXTRA_MEMBER_USERNAME, profile.getUsername());
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
