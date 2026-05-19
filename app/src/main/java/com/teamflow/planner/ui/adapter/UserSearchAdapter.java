package com.teamflow.planner.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class UserSearchAdapter extends ArrayAdapter<SupabaseService.Profile> {

    private final List<SupabaseService.Profile> profiles;

    public UserSearchAdapter(@NonNull Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line);
        this.profiles = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return profiles.size();
    }

    @Nullable
    @Override
    public SupabaseService.Profile getItem(int position) {
        return profiles.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        }

        SupabaseService.Profile profile = getItem(position);
        if (profile != null) {
            TextView textView = (TextView) convertView.findViewById(android.R.id.text1);
            textView.setText(profile.getName() + " (" + profile.getEmail() + ")");
        }

        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint != null && constraint.length() >= 1) {
                    CountDownLatch latch = new CountDownLatch(1);
                    SupabaseService.searchProfiles(constraint.toString(), new SupabaseCallback<List<SupabaseService.Profile>>() {
                        @Override
                        public void onSuccess(List<SupabaseService.Profile> value) {
                            profiles.clear();
                            profiles.addAll(value);
                            results.values = value;
                            results.count = value.size();
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable error) {
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                if (resultValue instanceof SupabaseService.Profile) {
                    return ((SupabaseService.Profile) resultValue).getName();
                }
                return super.convertResultToString(resultValue);
            }
        };
    }
}
