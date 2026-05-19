package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.databinding.ItemInvitationBinding;
import com.teamflow.planner.supabase.SupabaseService;

import java.util.ArrayList;
import java.util.List;

public class InvitationAdapter extends RecyclerView.Adapter<InvitationAdapter.ViewHolder> {

    private final List<SupabaseService.Invitation> list = new ArrayList<>();
    private final Listener listener;

    public interface Listener {
        void onAccept(SupabaseService.Invitation invitation);
        void onReject(SupabaseService.Invitation invitation);
    }

    public InvitationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<SupabaseService.Invitation> invitations) {
        list.clear();
        list.addAll(invitations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemInvitationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SupabaseService.Invitation item = list.get(position);
        holder.binding.textProjectName.setText(item.getProject_name());
        
        String inviter = item.getInviter_username();
        if (inviter == null || inviter.isEmpty()) {
            inviter = item.getInviter_email();
        }
        holder.binding.textInviterEmail.setText("Invited by: " + inviter);

        holder.binding.buttonAccept.setOnClickListener(v -> listener.onAccept(item));
        holder.binding.buttonReject.setOnClickListener(v -> listener.onReject(item));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemInvitationBinding binding;

        ViewHolder(ItemInvitationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
