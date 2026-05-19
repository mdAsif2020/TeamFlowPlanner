package com.teamflow.planner;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Entry point activity that routes the user to either Login or Dashboard
 * depending on their authentication state.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager sessionManager = new SessionManager(this);

        // Check login state via SessionManager (transitioned from Firebase)
        if (sessionManager.isLoggedIn()) {
            // User is already logged in, go to Dashboard
            startActivity(new Intent(this, DashboardActivity.class));
        } else {
            // No session, go to Login
            startActivity(new Intent(this, LoginActivity.class));
        }
        
        // Close MainActivity so user can't navigate back to it
        finish();
    }
}
