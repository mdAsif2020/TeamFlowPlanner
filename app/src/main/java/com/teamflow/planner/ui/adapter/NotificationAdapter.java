package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.R;
import com.teamflow.planner.data.entity.Notification;
import com.teamflow.planner.supabase.SupabaseService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface Listener {
        void onAcceptInvitation(SupabaseService.Invitation invitation, Notification notification);
        void onRejectInvitation(SupabaseService.Invitation invitation, Notification notification);
        void onNotificationClick(Notification notification);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault());

    public NotificationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<NotificationItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = items.get(position);
        holder.textTitle.setText(item.notification.title);
        holder.textMessage.setText(item.notification.message);
        holder.textTime.setText(dateFormat.format(new Date(item.notification.timestamp)));

        if ("INVITATION".equals(item.notification.type) && item.invitation != null) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.buttonAccept.setOnClickListener(v -> listener.onAcceptInvitation(item.invitation, item.notification));
            holder.buttonReject.setOnClickListener(v -> listener.onRejectInvitation(item.invitation, item.notification));
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(item.notification));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textTitle, textMessage, textTime;
        View layoutActions, buttonAccept, buttonReject;

        ViewHolder(View v) {
            super(v);
            textTitle = v.findViewById(R.id.textTitle);
            textMessage = v.findViewById(R.id.textMessage);
            textTime = v.findViewById(R.id.textTime);
            layoutActions = v.findViewById(R.id.layoutActions);
            buttonAccept = v.findViewById(R.id.buttonAccept);
            buttonReject = v.findViewById(R.id.buttonReject);
        }
    }

    public static class NotificationItem {
        public Notification notification;
        public SupabaseService.Invitation invitation;

        public NotificationItem(Notification notification, SupabaseService.Invitation invitation) {
            this.notification = notification;
            this.invitation = invitation;
        }
    }
}
