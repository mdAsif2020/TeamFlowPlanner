package com.teamflow.planner.supabase;

public interface SupabaseCallback<T> {
    void onSuccess(T value);
    void onError(Throwable error);
}

