package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.data.TaskWithProject;
import com.teamflow.planner.databinding.ItemMemberTaskBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Cross-project list of tasks for one assignee name.
 */
public class MemberTasksAdapter extends RecyclerView.Adapter<MemberTasksAdapter.VH> {

    public interface Listener {
        void onTaskClick(@NonNull TaskWithProject row);
    }

    private final Listener listener;
    private final List<TaskWithProject> items = new ArrayList<>();
    private final SimpleDateFormat deadlineFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public MemberTasksAdapter(Listener listener) {
        this.listener = listener;
        deadlineFmt.setTimeZone(TimeZone.getDefault());
    }

    public void submitList(List<TaskWithProject> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemMemberTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemMemberTaskBinding binding;

        VH(ItemMemberTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TaskWithProject row) {
            binding.textProject.setText(row.projectName);
            binding.textTaskTitle.setText(row.task.title);
            if (row.task.deadline != null) {
                binding.textDeadline.setVisibility(android.view.View.VISIBLE);
                binding.textDeadline.setText(deadlineFmt.format(new Date(row.task.deadline)));
            } else {
                binding.textDeadline.setVisibility(android.view.View.GONE);
            }
            binding.getRoot().setOnClickListener(v -> listener.onTaskClick(row));
        }
    }
}
