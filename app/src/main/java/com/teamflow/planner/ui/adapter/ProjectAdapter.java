package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.data.ProjectListItem;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.databinding.ItemProjectBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard list of projects with per-project progress and search filtering.
 */
public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {

    public interface Listener {
        void onProjectClick(@NonNull Project project);
        void onProjectOverflow(@NonNull Project project, View anchor);
    }

    private final Listener listener;
    private final List<ProjectListItem> fullList = new ArrayList<>();
    private final List<ProjectListItem> filteredList = new ArrayList<>();
    private String currentQuery = "";

    public ProjectAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitSource(List<ProjectListItem> next) {
        fullList.clear();
        if (next != null) {
            fullList.addAll(next);
        }
        applyFilter();
    }

    public void setSearchQuery(String query) {
        this.currentQuery = query == null ? "" : query.toLowerCase().trim();
        applyFilter();
    }

    private void applyFilter() {
        filteredList.clear();
        if (currentQuery.isEmpty()) {
            filteredList.addAll(fullList);
        } else {
            for (ProjectListItem item : fullList) {
                if (item.project.name.toLowerCase().contains(currentQuery) ||
                    item.project.description.toLowerCase().contains(currentQuery)) {
                    filteredList.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemProjectBinding binding = ItemProjectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(filteredList.get(position));
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    final class VH extends RecyclerView.ViewHolder {
        private final ItemProjectBinding binding;

        VH(ItemProjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ProjectListItem row) {
            Project p = row.project;
            binding.textTitle.setText(p.name);
            binding.textSubtitle.setText(p.description.isEmpty()
                    ? binding.getRoot().getContext().getString(com.teamflow.planner.R.string.no_description)
                    : p.description);

            int total = row.taskCount;
            int done = row.completedCount;
            binding.textCounts.setText(String.format(Locale.getDefault(),
                    binding.getRoot().getContext().getString(com.teamflow.planner.R.string.project_task_counts),
                    done, total));

            binding.progressProject.setMax(Math.max(total, 1));
            binding.progressProject.setProgress(total == 0 ? 0 : done);

            binding.chipCompleted.setVisibility(p.isCompleted ? View.VISIBLE : View.GONE);

            binding.cardProject.setOnClickListener(v -> listener.onProjectClick(p));
            binding.buttonMore.setOnClickListener(v -> listener.onProjectOverflow(p, v));
        }
    }
}
