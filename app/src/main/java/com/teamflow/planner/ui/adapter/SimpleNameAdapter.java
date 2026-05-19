package com.teamflow.planner.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.teamflow.planner.data.entity.User;
import com.teamflow.planner.databinding.ItemSimpleNameBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-line name rows (e.g. assignee directory).
 */
public class SimpleNameAdapter extends RecyclerView.Adapter<SimpleNameAdapter.VH> {

    public interface Listener {
        void onNameClick(@NonNull String name);
        void onMoreClick(@NonNull View anchor, @NonNull String name);
    }

    private final Listener listener;
    private final List<User> items = new ArrayList<>();

    public SimpleNameAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<User> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemSimpleNameBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
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
        private final ItemSimpleNameBinding binding;

        VH(ItemSimpleNameBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(User user) {
            String name = user.name;
            binding.textName.setText(name);
            
            // Modern Avatar Logic: Show image if available, else first letter
            if (user.photoUrl != null && !user.photoUrl.isEmpty()) {
                binding.textAvatarLetter.setVisibility(View.GONE);
                binding.imageProfile.setVisibility(View.VISIBLE);
                Glide.with(binding.imageProfile.getContext())
                        .load(user.photoUrl)
                        .circleCrop()
                        .into(binding.imageProfile);
            } else {
                binding.imageProfile.setVisibility(View.GONE);
                binding.textAvatarLetter.setVisibility(View.VISIBLE);
                if (name != null && !name.isEmpty()) {
                    binding.textAvatarLetter.setText(String.valueOf(name.charAt(0)).toUpperCase());
                } else {
                    binding.textAvatarLetter.setText("?");
                }
            }

            binding.getRoot().setOnClickListener(v -> listener.onNameClick(name));
            binding.iconArrow.setOnClickListener(v -> listener.onMoreClick(v, name));
        }
    }
}
