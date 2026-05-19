package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teamflow.planner.databinding.ActivityRegisterBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        binding.buttonRegister.setOnClickListener(v -> {
            String name = binding.inputName.getText().toString().trim();
            String email = binding.inputEmail.getText().toString().trim();
            String password = binding.inputPassword.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("name", name);
            SupabaseService.signUp(email, password, metadata, new SupabaseCallback<>() {
                @Override
                public void onSuccess(String userId) {
                    // Create initial profile record
                    SupabaseService.Profile initialProfile = new SupabaseService.Profile(
                            userId, name, email, null, null, null
                    );
                    SupabaseService.updateProfile(initialProfile, new SupabaseCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(() -> {
                                sessionManager.createSession(userId, name, email);
                                Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                                finish();
                            });
                        }

                        @Override
                        public void onError(Throwable error) {
                            // Even if profile creation fails, the user is created in Auth
                            runOnUiThread(() -> {
                                sessionManager.createSession(userId, name, email);
                                startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                                finish();
                            });
                        }
                    });
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this, "Registration Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        binding.buttonToLogin.setOnClickListener(v -> finish());
    }
}
