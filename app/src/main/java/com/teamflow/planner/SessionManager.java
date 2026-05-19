package com.teamflow.planner;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "TeamFlowPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_USERNAME = "userUsername";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_PHOTO_URL = "userPhotoUrl";

    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createSession(String id, String name, String username, String email, String photoUrl) {
        editor.putString(KEY_USER_ID, id);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_USERNAME, username);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_PHOTO_URL, photoUrl);
        editor.apply();
    }

    public boolean isLoggedIn() {
        try {
            return pref.getString(KEY_USER_ID, null) != null;
        } catch (ClassCastException e) {
            logout();
            return false;
        }
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, "Guest");
    }

    public String getUserUsername() {
        return pref.getString(KEY_USER_USERNAME, null);
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, null);
    }

    public String getUserPhotoUrl() {
        return pref.getString(KEY_USER_PHOTO_URL, null);
    }

    public void updateUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    public void updateUserUsername(String username) {
        editor.putString(KEY_USER_USERNAME, username);
        editor.apply();
    }

    public void updateUserPhotoUrl(String photoUrl) {
        editor.putString(KEY_USER_PHOTO_URL, photoUrl);
        editor.apply();
    }

    public String getUserId() {
        try {
            return pref.getString(KEY_USER_ID, null);
        } catch (ClassCastException e) {
            return null;
        }
    }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}
