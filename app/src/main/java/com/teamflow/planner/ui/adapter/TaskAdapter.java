package com.teamflow.planner.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import com.teamflow.planner.data.TaskStatus;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.databinding.ItemTaskBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Task list inside a project: deadlines, status chip, overdue highlight, description.
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface Listener {
        void onTaskClick(@NonNull Task task);
    }

    private final Listener listener;
    private final List<Task> items = new ArrayList<>();
    private final SimpleDateFormat deadlineFmt = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public TaskAdapter(Listener listener) {
        this.listener = listener;
        deadlineFmt.setTimeZone(TimeZone.getDefault());
    }

    public void submitList(List<Task> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    public Task getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTaskBinding binding = ItemTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static long startOfTodayMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    final class VH extends RecyclerView.ViewHolder {
        private final ItemTaskBinding binding;

        VH(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Task task) {
            binding.textTitle.setText(task.title);

            if (task.description != null && !task.description.isEmpty()) {
                binding.textDescription.setVisibility(View.VISIBLE);
                binding.textDescription.setText(task.description);
            } else {
                binding.textDescription.setVisibility(View.GONE);
            }

            android.content.Context ctx = binding.getRoot().getContext();

            binding.textAssignee.setText(task.assignee.isEmpty()
                    ? binding.getRoot().getContext().getString(com.teamflow.planner.R.string.unassigned)
                    : task.assignee);

            if (task.deadline != null) {
                binding.textDeadline.setVisibility(View.VISIBLE);
                binding.textDeadline.setText(deadlineFmt.format(new Date(task.deadline)));
            } else {
                binding.textDeadline.setVisibility(View.GONE);
            }

            int statusColor;
            switch (task.status) {
                case IN_PROGRESS:
                    binding.chipStatus.setText(com.teamflow.planner.R.string.status_in_progress);
                    statusColor = ContextCompat.getColor(ctx, com.teamflow.planner.R.color.status_in_progress);
                    break;
                case COMPLETED:
                    binding.chipStatus.setText(com.teamflow.planner.R.string.status_completed);
                    statusColor = ContextCompat.getColor(ctx, com.teamflow.planner.R.color.status_completed);
                    break;
                case PENDING:
                default:
                    binding.chipStatus.setText(com.teamflow.planner.R.string.status_pending);
                    statusColor = ContextCompat.getColor(ctx, com.teamflow.planner.R.color.status_pending);
                    break;
            }
            binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(statusColor));
            binding.chipStatus.setTextColor(ContextCompat.getColor(ctx, android.R.color.white));

            boolean overdue = task.status != TaskStatus.COMPLETED
                    && task.deadline != null
                    && task.deadline < startOfTodayMillis();
            int stroke = binding.getRoot().getContext().getResources()
                    .getDimensionPixelSize(com.teamflow.planner.R.dimen.card_stroke_width);
            if (overdue) {
                binding.cardTask.setStrokeWidth(stroke);
                binding.cardTask.setStrokeColor(ContextCompat.getColor(
                        binding.getRoot().getContext(), com.teamflow.planner.R.color.overdue_red));
            } else {
                binding.cardTask.setStrokeWidth(0);
            }

            int surface = MaterialColors.getColor(binding.getRoot(), com.google.android.material.R.attr.colorSurface);
            binding.cardTask.setCardBackgroundColor(surface);

            binding.cardTask.setOnClickListener(v -> listener.onTaskClick(task));
        }
    }
}
