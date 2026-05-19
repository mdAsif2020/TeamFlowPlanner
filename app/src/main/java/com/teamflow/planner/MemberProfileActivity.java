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

    private ActivityMemberProfileBinding binding;
    private SessionManager sessionManager;
    private String memberName;
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
        if (memberName == null || memberName.isEmpty()) {
            finish();
            return;
        }

        sessionManager = new SessionManager(this);

        boolean isMyProfile = memberName.equals(sessionManager.getUserName());

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
            String bio = binding.inputBio.getText().toString().trim();
            String phone = binding.inputPhone.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            SupabaseService.Profile updatedProfile = new SupabaseService.Profile(
                    sessionManager.getUserId(),
                    name,
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

    private void setupOtherMemberProfile() {
        binding.layoutMemberDetails.setVisibility(View.VISIBLE);
        // Email will be loaded from fetchProfile
        
        binding.buttonInviteToProject.setOnClickListener(v -> {
            Toast.makeText(this, "Invitation feature coming soon for " + memberName, Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProfileData() {
        boolean isMyProfile = memberName.equals(sessionManager.getUserName());
        
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
        } else {
            SupabaseService.fetchProfile(memberName, callback);
        }
    }

    private void uploadProfileImage() {
        if (selectedImageUri == null) return;
        
        try (InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
             ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {
            
            if (inputStream == null) return;

            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] imageBytes = byteBuffer.toByteArray();
            
            String userId = String.valueOf(sessionManager.getUserId());
            
            SupabaseService.uploadProfileImage(userId, imageBytes, new SupabaseCallback<>() {
                @Override
                public void onSuccess(String url) {
                    // Now update profile with the new photo URL
                    SupabaseService.Profile update = new SupabaseService.Profile(
                            userId,
                            sessionManager.getUserName(),
                            sessionManager.getUserEmail(),
                            binding.inputBio.getText().toString(),
                            binding.inputPhone.getText().toString(),
                            url
                    );
                    
                    SupabaseService.updateProfile(update, new SupabaseCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(() -> {
                                sessionManager.updateUserPhotoUrl(url);
                                loadProfileImage(url);
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
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProfileImage(String url) {
        binding.textAvatarLetter.setVisibility(View.GONE);
        binding.imageProfile.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(url)
                .centerCrop()
                .into(binding.imageProfile);
    }
}
