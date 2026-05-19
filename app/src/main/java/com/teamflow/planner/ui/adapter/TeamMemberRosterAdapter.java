package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.databinding.ItemTeamMemberBinding;

import java.util.ArrayList;
import java.util.List;

public class TeamMemberRosterAdapter extends RecyclerView.Adapter<TeamMemberRosterAdapter.VH> {

    public interface Listener {
        void onViewTasks(@NonNull TeamMember member);

        void onRemove(@NonNull TeamMember member);
    }

    private final Listener listener;
    private final List<TeamMember> items = new ArrayList<>();

    public TeamMemberRosterAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<TeamMember> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemTeamMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class VH extends RecyclerView.ViewHolder {
        private final ItemTeamMemberBinding binding;

        VH(ItemTeamMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TeamMember m) {
            binding.textMemberName.setText(m.name);
            binding.buttonViewTasks.setOnClickListener(v -> listener.onViewTasks(m));
            binding.buttonRemoveMember.setOnClickListener(v -> listener.onRemove(m));
        }
    }
}
