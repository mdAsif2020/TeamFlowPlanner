package com.teamflow.planner;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.databinding.ActivityInvitationsBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.InvitationAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InvitationsActivity extends AppCompatActivity {

    private ActivityInvitationsBinding binding;
    private InvitationAdapter adapter;
    private SessionManager sessionManager;
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInvitationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        db = AppDatabase.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        adapter = new InvitationAdapter(new InvitationAdapter.Listener() {
            @Override
            public void onAccept(SupabaseService.Invitation invitation) {
                acceptInvitation(invitation);
            }

            @Override
            public void onReject(SupabaseService.Invitation invitation) {
                rejectInvitation(invitation);
            }
        });

        binding.recyclerInvitations.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerInvitations.setAdapter(adapter);

        loadInvitations();
    }

    private void loadInvitations() {
        SupabaseService.fetchInvitations(sessionManager.getUserEmail(), new SupabaseCallback<List<SupabaseService.Invitation>>() {
            @Override
            public void onSuccess(List<SupabaseService.Invitation> invitations) {
                runOnUiThread(() -> {
                    adapter.submitList(invitations);
                    binding.textNoInvitations.setVisibility(invitations.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(InvitationsActivity.this, "Failed to load invitations", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void acceptInvitation(SupabaseService.Invitation invitation) {
        SupabaseService.updateInvitationStatus(invitation.getId(), "ACCEPTED", new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Fetch project details to join it
                SupabaseService.fetchProjectById(invitation.getProject_id(), new SupabaseCallback<SupabaseService.ProjectSync>() {
                    @Override
                    public void onSuccess(SupabaseService.ProjectSync pSync) {
                        io.execute(() -> {
                            Project p = new Project();
                            p.remoteId = pSync.getId();
                            p.name = pSync.getName();
                            p.description = pSync.getDescription();
                            p.ownerEmail = pSync.getOwner_email();
                            p.createdAt = pSync.getCreated_at();
                            p.lastModified = System.currentTimeMillis();
                            
                            long projectId = db.projectDao().insert(p);

                            // Add current user as a team member of this project locally
                            TeamMember member = new TeamMember();
                            member.projectId = projectId;
                            member.name = sessionManager.getUserName();
                            member.createdAt = System.currentTimeMillis();
                            db.teamMemberDao().insert(member);

                            // Sync membership to Supabase so owner can see us
                            SupabaseService.ProjectMemberSync memberSync = new SupabaseService.ProjectMemberSync(
                                    null,
                                    p.remoteId,
                                    sessionManager.getUserEmail(),
                                    sessionManager.getUserName()
                            );
                            SupabaseService.addProjectMember(memberSync, new SupabaseCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {
                                    // Membership synced
                                }

                                @Override
                                public void onError(Throwable error) {
                                    // Membership sync failed
                                }
                            });

                            runOnUiThread(() -> {
                                Toast.makeText(InvitationsActivity.this, "Joined project: " + p.name, Toast.LENGTH_SHORT).show();
                                loadInvitations();
                            });
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        runOnUiThread(() -> Toast.makeText(InvitationsActivity.this, "Failed to fetch project details", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(InvitationsActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void rejectInvitation(SupabaseService.Invitation invitation) {
        SupabaseService.updateInvitationStatus(invitation.getId(), "REJECTED", new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(InvitationsActivity.this, "Invitation declined", Toast.LENGTH_SHORT).show();
                    loadInvitations();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(InvitationsActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
