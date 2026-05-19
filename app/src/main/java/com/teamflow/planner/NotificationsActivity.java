package com.teamflow.planner;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.teamflow.planner.data.AppDatabase;
import com.teamflow.planner.data.entity.Notification;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.databinding.ActivityNotificationsBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;
import com.teamflow.planner.ui.adapter.NotificationAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private NotificationAdapter adapter;
    private SessionManager sessionManager;
    private AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);
        db = AppDatabase.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        adapter = new NotificationAdapter(new NotificationAdapter.Listener() {
            @Override
            public void onAcceptInvitation(SupabaseService.Invitation invitation, Notification notification) {
                acceptInvitation(invitation, notification);
            }

            @Override
            public void onRejectInvitation(SupabaseService.Invitation invitation, Notification notification) {
                rejectInvitation(invitation, notification);
            }

            @Override
            public void onNotificationClick(Notification notification) {
                if (!notification.isRead) {
                    notification.isRead = true;
                    io.execute(() -> db.notificationDao().update(notification));
                }
            }
        });

        binding.recyclerNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerNotifications.setAdapter(adapter);

        io.execute(() -> db.notificationDao().markAllAsRead());

        loadNotifications();
    }

    private void loadNotifications() {
        // Load local notifications (updates)
        db.notificationDao().observeAll().observe(this, notifications -> {
            // Also fetch invitations to merge
            fetchInvitations(notifications);
        });
    }

    private void fetchInvitations(List<Notification> localNotifications) {
        SupabaseService.fetchInvitations(sessionManager.getUserEmail(), new SupabaseCallback<List<SupabaseService.Invitation>>() {
            @Override
            public void onSuccess(List<SupabaseService.Invitation> invitations) {
                List<NotificationAdapter.NotificationItem> items = new ArrayList<>();
                
                // Add invitations
                for (SupabaseService.Invitation inv : invitations) {
                    Notification n = new Notification(
                        "New Invitation",
                        inv.getInviter_username() + " invited you to join '" + inv.getProject_name() + "'",
                        "INVITATION"
                    );
                    n.timestamp = System.currentTimeMillis(); // Approximate
                    items.add(new NotificationAdapter.NotificationItem(n, inv));
                }

                // Add local notifications (updates)
                for (Notification n : localNotifications) {
                    if (!"INVITATION".equals(n.type)) {
                        items.add(new NotificationAdapter.NotificationItem(n, null));
                    }
                }

                // Sort items by timestamp desc
                items.sort((a, b) -> Long.compare(b.notification.timestamp, a.notification.timestamp));

                runOnUiThread(() -> {
                    adapter.submitList(items);
                    binding.textNoNotifications.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Throwable error) {
                // Just show local if invitations fail
                List<NotificationAdapter.NotificationItem> items = new ArrayList<>();
                for (Notification n : localNotifications) {
                    items.add(new NotificationAdapter.NotificationItem(n, null));
                }
                runOnUiThread(() -> {
                    adapter.submitList(items);
                    binding.textNoNotifications.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void acceptInvitation(SupabaseService.Invitation invitation, Notification notification) {
        SupabaseService.updateInvitationStatus(invitation.getId(), "ACCEPTED", new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
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
                            
                            p.id = db.projectDao().insert(p);

                            TeamMember member = new TeamMember();
                            member.projectId = p.id;
                            member.name = sessionManager.getUserName();
                            member.username = sessionManager.getUserUsername();
                            member.createdAt = System.currentTimeMillis();
                            db.teamMemberDao().insert(member);

                            SupabaseService.ProjectMemberSync memberSync = new SupabaseService.ProjectMemberSync(
                                    null, p.remoteId, sessionManager.getUserEmail(),
                                    sessionManager.getUserName(), sessionManager.getUserUsername()
                            );
                            SupabaseService.addProjectMember(memberSync, new SupabaseCallback<Void>() {
                                @Override
                                public void onSuccess(Void result) {}
                                @Override
                                public void onError(Throwable error) {}
                            });

                            runOnUiThread(() -> {
                                Toast.makeText(NotificationsActivity.this, "Joined project: " + p.name, Toast.LENGTH_SHORT).show();
                                loadNotifications();
                            });
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        runOnUiThread(() -> Toast.makeText(NotificationsActivity.this, "Failed to fetch project details", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void rejectInvitation(SupabaseService.Invitation invitation, Notification notification) {
        SupabaseService.updateInvitationStatus(invitation.getId(), "REJECTED", new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(NotificationsActivity.this, "Invitation declined", Toast.LENGTH_SHORT).show();
                    loadNotifications();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
