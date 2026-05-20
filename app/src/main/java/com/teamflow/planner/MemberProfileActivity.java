package com.teamflow.planner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.teamflow.planner.databinding.ActivityMemberProfileBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MemberProfileActivity extends AppCompatActivity {

    public static final String EXTRA_MEMBER_NAME = "extra_member_name";
    public static final String EXTRA_MEMBER_EMAIL = "extra_member_email";
    public static final String EXTRA_MEMBER_USERNAME = "extra_member_username";

    private ActivityMemberProfileBinding binding;
    private SessionManager sessionManager;
    private String memberName;
    private String memberEmail;
    private String memberUsername;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    uploadProfileImage();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        memberName = getIntent().getStringExtra(EXTRA_MEMBER_NAME);
        memberEmail = getIntent().getStringExtra(EXTRA_MEMBER_EMAIL);
        memberUsername = getIntent().getStringExtra(EXTRA_MEMBER_USERNAME);
        
        if (memberName == null || memberName.isEmpty()) {
            finish();
            return;
        }

        sessionManager = new SessionManager(this);

        boolean isMyProfile = (memberEmail != null && memberEmail.equalsIgnoreCase(sessionManager.getUserEmail())) ||
                             (memberUsername != null && memberUsername.equalsIgnoreCase(sessionManager.getUserUsername())) ||
                             (memberName != null && memberName.equalsIgnoreCase(sessionManager.getUserName()));

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isMyProfile ? "My Profile" : "Member Profile");
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        binding.textMemberName.setText(memberName);
        binding.textAvatarLetter.setText(String.valueOf(memberName.charAt(0)).toUpperCase());

        loadProfileData();

        if (isMyProfile) {
            setupMyProfile();
        } else {
            setupOtherMemberProfile();
        }

        binding.buttonRemovePhoto.setOnClickListener(v -> removeProfileImage());

        binding.buttonViewActiveTasks.setOnClickListener(v -> {
            Intent i = new Intent(this, MemberTasksActivity.class);
            i.putExtra(MemberTasksActivity.EXTRA_ASSIGNEE_NAME, memberName);
            startActivity(i);
        });
    }

    private void setupMyProfile() {
        binding.textMemberEmail.setText(sessionManager.getUserEmail());
        binding.buttonInviteToProject.setVisibility(View.GONE);
        binding.layoutMyDetails.setVisibility(View.VISIBLE);
        binding.buttonLogout.setVisibility(View.VISIBLE);

        // Optional: fetch profile details from Supabase `profiles` table.

        binding.buttonSaveDetails.setOnClickListener(v -> {
            String name = binding.inputName.getText().toString().trim();
            String username = binding.inputUsername.getText().toString().trim();
            String bio = binding.inputBio.getText().toString().trim();
            String phone = binding.inputPhone.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            SupabaseService.Profile updatedProfile = new SupabaseService.Profile(
                    sessionManager.getUserId(),
                    name,
                    username,
                    sessionManager.getUserEmail(),
                    bio,
                    phone,
                    sessionManager.getUserPhotoUrl()
            );

            SupabaseService.updateProfile(updatedProfile, new SupabaseCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> {
                        sessionManager.updateUserName(name);
                        binding.textMemberName.setText(name);
                        Toast.makeText(MemberProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Failed to update profile", Toast.LENGTH_SHORT).show());
                }
            });
        });

        binding.buttonLogout.setOnClickListener(v -> {
            sessionManager.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        binding.cardProfileImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });
    }

    private String targetMemberEmail;

    private void setupOtherMemberProfile() {
        binding.layoutMemberDetails.setVisibility(View.VISIBLE);
        
        binding.buttonInviteToProject.setOnClickListener(v -> {
            if (targetMemberEmail == null) {
                Toast.makeText(this, "Fetching member details, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            showProjectSelectionDialog();
        });
    }

    private void showProjectSelectionDialog() {
        new Thread(() -> {
            com.teamflow.planner.data.AppDatabase db = com.teamflow.planner.data.AppDatabase.getInstance(this);
            java.util.List<com.teamflow.planner.data.entity.Project> projects = db.projectDao().getAllProjectsSync();
            
            runOnUiThread(() -> {
                if (projects.isEmpty()) {
                    Toast.makeText(this, "You have no projects to invite them to.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] projectNames = new String[projects.size()];
                for (int i = 0; i < projects.size(); i++) {
                    projectNames[i] = projects.get(i).name;
                }

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Select Project")
                        .setItems(projectNames, (dialog, which) -> {
                            com.teamflow.planner.data.entity.Project selected = projects.get(which);
                            sendInvitation(selected);
                        })
                        .show();
            });
        }).start();
    }

    private void sendInvitation(com.teamflow.planner.data.entity.Project project) {
        if (project.remoteId == null) {
            Toast.makeText(this, "Project not synced to cloud yet", Toast.LENGTH_SHORT).show();
            return;
        }
        SupabaseService.Invitation invite = new SupabaseService.Invitation(
                null,
                project.remoteId,
                project.name,
                sessionManager.getUserEmail(),
                sessionManager.getUserUsername(),
                targetMemberEmail,
                "PENDING"
        );

        SupabaseService.sendInvitation(invite, new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Invitation sent!", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Failed to send invitation", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadProfileData() {
        boolean isMyProfile = (memberEmail != null && memberEmail.equalsIgnoreCase(sessionManager.getUserEmail())) ||
                             (memberUsername != null && memberUsername.equalsIgnoreCase(sessionManager.getUserUsername())) ||
                             (memberName != null && memberName.equalsIgnoreCase(sessionManager.getUserName()));
        
        SupabaseCallback<SupabaseService.Profile> callback = new SupabaseCallback<>() {
            @Override
            public void onSuccess(SupabaseService.Profile profile) {
                runOnUiThread(() -> {
                    if (profile.getPhoto_url() != null) {
                        loadProfileImage(profile.getPhoto_url());
                    }
                    if (profile.getName() != null) {
                        binding.textMemberName.setText(profile.getName());
                        binding.inputName.setText(profile.getName());
                    }
                    if (profile.getUsername() != null) {
                        binding.textMemberUsername.setText("@" + profile.getUsername());
                        binding.inputUsername.setText(profile.getUsername());
                    }
                    if (profile.getBio() != null) {
                        binding.textBio.setText(profile.getBio());
                        binding.inputBio.setText(profile.getBio());
                    }
                    if (profile.getPhone() != null) {
                        binding.textPhone.setText(profile.getPhone());
                        binding.inputPhone.setText(profile.getPhone());
                    }
                    if (profile.getEmail() != null) {
                        binding.textMemberEmail.setText(profile.getEmail());
                        targetMemberEmail = profile.getEmail();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                // If profile not found, just use defaults
            }
        };

        if (isMyProfile) {
            SupabaseService.fetchProfileById(sessionManager.getUserId(), callback);
        } else if (memberEmail != null) {
            SupabaseService.fetchProfileByEmail(memberEmail, callback);
        } else if (memberUsername != null) {
            SupabaseService.fetchProfileByUsername(memberUsername, callback);
        } else {
            SupabaseService.fetchProfile(memberName, callback);
        }
    }

    private void uploadProfileImage() {
        if (selectedImageUri == null) return;
        
        // Capture data on UI thread
        String bio = binding.inputBio.getText().toString().trim();
        String phone = binding.inputPhone.getText().toString().trim();
        String name = binding.inputName.getText().toString().trim();
        String username = binding.inputUsername.getText().toString().trim();
        String userId = sessionManager.getUserId();
        String email = sessionManager.getUserEmail();

        if (userId == null) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Processing and uploading in background
        new Thread(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                 ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {
                
                if (inputStream == null) return;

                byte[] buffer = new byte[4096];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] imageBytes = byteBuffer.toByteArray();
                
                SupabaseService.uploadProfileImage(userId, imageBytes, new SupabaseCallback<String>() {
                    @Override
                    public void onSuccess(String url) {
                        // Use captured values to avoid accessing UI from background thread
                        SupabaseService.Profile update = new SupabaseService.Profile(
                                userId, name, username, email, bio, phone, url
                        );
                        
                        SupabaseService.updateProfile(update, new SupabaseCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                runOnUiThread(() -> {
                                    sessionManager.updateUserPhotoUrl(url);
                                    // Pass true to force refresh cache
                                    loadProfileImage(url, true);
                                    Toast.makeText(MemberProfileActivity.this, "Profile image updated!", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onError(Throwable error) {
                                runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Failed to update profile record", Toast.LENGTH_SHORT).show());
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable error) {
                        runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Upload failed: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void removeProfileImage() {
        String userId = sessionManager.getUserId();
        if (userId == null) return;

        String name = binding.inputName.getText().toString().trim();
        String username = binding.inputUsername.getText().toString().trim();
        String bio = binding.inputBio.getText().toString().trim();
        String phone = binding.inputPhone.getText().toString().trim();
        String email = sessionManager.getUserEmail();

        SupabaseService.Profile update = new SupabaseService.Profile(
                userId, name, username, email, bio, phone, null
        );

        SupabaseService.updateProfile(update, new SupabaseCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    sessionManager.updateUserPhotoUrl(null);
                    binding.imageProfile.setVisibility(View.GONE);
                    binding.textAvatarLetter.setVisibility(View.VISIBLE);
                    binding.buttonRemovePhoto.setVisibility(View.GONE);
                    Toast.makeText(MemberProfileActivity.this, "Photo removed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(MemberProfileActivity.this, "Failed to remove photo", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadProfileImage(String url) {
        loadProfileImage(url, false);
    }

    private void loadProfileImage(String url, boolean forceRefresh) {
        if (url == null || url.isEmpty()) {
            binding.imageProfile.setVisibility(View.GONE);
            binding.textAvatarLetter.setVisibility(View.VISIBLE);
            binding.buttonRemovePhoto.setVisibility(View.GONE);
            return;
        }

        boolean isMyProfile = memberName.equals(sessionManager.getUserName());
        if (isMyProfile) {
            binding.buttonRemovePhoto.setVisibility(View.VISIBLE);
        }

        binding.textAvatarLetter.setVisibility(View.GONE);
        binding.imageProfile.setVisibility(View.VISIBLE);

        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> builder = Glide.with(this)
                .load(url)
                .centerCrop();

        if (forceRefresh) {
            builder = builder.signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()));
        }

        builder.into(binding.imageProfile);
    }
}
