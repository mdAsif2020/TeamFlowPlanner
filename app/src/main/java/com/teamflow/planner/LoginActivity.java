package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.teamflow.planner.databinding.ActivityLoginBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonLogin.setOnClickListener(v -> {
            String email = binding.inputEmail.getText().toString().trim();
            String password = binding.inputPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            SupabaseService.signIn(email, password, new SupabaseCallback<String>() {
                @Override
                public void onSuccess(String userId) {
                    // After successful sign in, fetch the profile to get the user's name
                    SupabaseService.fetchProfileById(userId, new SupabaseCallback<SupabaseService.Profile>() {
                        @Override
                        public void onSuccess(SupabaseService.Profile profile) {
                            runOnUiThread(() -> {
                                String name = (profile != null && profile.getName() != null) ? profile.getName() : "User";
                                String photoUrl = (profile != null) ? profile.getPhoto_url() : null;
                                sessionManager.createSession(userId, name, email, photoUrl);
                                startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                                finish();
                            });
                        }

                        @Override
                        public void onError(Throwable error) {
                            // Fallback if profile fetch fails
                            runOnUiThread(() -> {
                                sessionManager.createSession(userId, "User", email);
                                startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                                finish();
                            });
                        }
                    });
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Login Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        binding.buttonForgotPassword.setOnClickListener(v -> {
            String email = binding.inputEmail.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email to reset password", Toast.LENGTH_SHORT).show();
                return;
            }

            SupabaseService.resetPassword(email, new SupabaseCallback<>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Reset link sent to " + email, Toast.LENGTH_LONG).show());
                }

                @Override
                public void onError(Throwable error) {
                    runOnUiThread(() -> Toast.makeText(LoginActivity.this, "Failed to send reset link: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        });

        binding.buttonToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }
}
