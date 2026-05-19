package com.teamflow.planner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.teamflow.planner.data.entity.ChatMessage;
import com.teamflow.planner.databinding.ActivityChatBinding;
import com.teamflow.planner.supabase.SupabaseCallback;
import com.teamflow.planner.supabase.SupabaseService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    public static final String EXTRA_OWNER_EMAIL = "extra_owner_email";

    private ActivityChatBinding binding;
    private String projectId;
    private String ownerEmail;
    private ChatAdapter adapter;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectId = String.valueOf(getIntent().getLongExtra(EXTRA_PROJECT_ID, -1));
        ownerEmail = getIntent().getStringExtra(EXTRA_OWNER_EMAIL);
        
        if (projectId.equals("-1") || ownerEmail == null) {
            finish();
            return;
        }

        sessionManager = new SessionManager(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ChatAdapter(sessionManager.getUserEmail());
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerChat.setLayoutManager(layoutManager);
        binding.recyclerChat.setAdapter(adapter);

        loadMessages();
        startRealtime();

        binding.buttonSend.setOnClickListener(v -> {
            String text = binding.inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
            }
        });
    }

    private void loadMessages() {
        long pid;
        try {
            pid = Long.parseLong(projectId);
        } catch (NumberFormatException nfe) {
            runOnUiThread(() -> Toast.makeText(this, "Invalid project id", Toast.LENGTH_SHORT).show());
            return;
        }

        SupabaseService.fetchMessages(pid, new SupabaseCallback<>() {
            @Override
            public void onSuccess(List<SupabaseService.MessageRow> rows) {
                List<ChatMessage> msgs = new ArrayList<>();
                for (SupabaseService.MessageRow r : rows) {
                    ChatMessage m = new ChatMessage();
                    m.senderName = r.getSender_name();
                    m.senderEmail = r.getSender_email();
                    m.message = r.getMessage();
                    Long ts = r.getTimestamp();
                    m.timestamp = ts == null ? System.currentTimeMillis() : ts;
                    msgs.add(m);
                }
                runOnUiThread(() -> {
                    adapter.setMessages(msgs);
                    if (!msgs.isEmpty()) {
                        binding.recyclerChat.smoothScrollToPosition(msgs.size() - 1);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Error loading messages: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startRealtime() {
        long pid;
        try {
            pid = Long.parseLong(projectId);
        } catch (NumberFormatException nfe) {
            return;
        }
        SupabaseService.observeMessages(pid, new SupabaseCallback<>() {
            @Override
            public void onSuccess(List<SupabaseService.MessageRow> rows) {
                List<ChatMessage> msgs = new ArrayList<>();
                for (SupabaseService.MessageRow r : rows) {
                    ChatMessage m = new ChatMessage();
                    m.senderName = r.getSender_name();
                    m.senderEmail = r.getSender_email();
                    m.message = r.getMessage();
                    Long ts = r.getTimestamp();
                    m.timestamp = ts == null ? System.currentTimeMillis() : ts;
                    msgs.add(m);
                }
                runOnUiThread(() -> {
                    adapter.setMessages(msgs);
                    if (!msgs.isEmpty()) {
                        binding.recyclerChat.smoothScrollToPosition(msgs.size() - 1);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Realtime error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendMessage(String text) {
        long pid;
        try {
            pid = Long.parseLong(projectId);
        } catch (NumberFormatException nfe) {
            runOnUiThread(() -> Toast.makeText(this, "Invalid project id", Toast.LENGTH_SHORT).show());
            return;
        }

        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();
        if (email == null) email = "";

        SupabaseService.sendMessage(pid, name, email, text, new SupabaseCallback<>() {
            @Override
            public void onSuccess(Void ignored) {
                runOnUiThread(() -> binding.inputMessage.setText(""));
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "Error sending: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_SENT = 1;
        private static final int VIEW_TYPE_RECEIVED = 2;

        private final List<ChatMessage> messages = new ArrayList<>();
        private final String myEmail;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        ChatAdapter(String myEmail) {
            this.myEmail = myEmail;
        }

        void setMessages(List<ChatMessage> newMessages) {
            messages.clear();
            messages.addAll(newMessages);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            if (messages.get(position).senderEmail != null && messages.get(position).senderEmail.equals(myEmail)) {
                return VIEW_TYPE_SENT;
            } else {
                return VIEW_TYPE_RECEIVED;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_SENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_sent, parent, false);
                return new SentViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_received, parent, false);
                return new ReceivedViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            String time = timeFormat.format(new Date(msg.timestamp));

            if (holder instanceof SentViewHolder) {
                ((SentViewHolder) holder).bind(msg, time);
            } else {
                ((ReceivedViewHolder) holder).bind(msg, time);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class SentViewHolder extends RecyclerView.ViewHolder {
            TextView textMessage, textTime;
            SentViewHolder(View v) {
                super(v);
                textMessage = v.findViewById(R.id.textMessage);
                textTime = v.findViewById(R.id.textTime);
            }
            void bind(ChatMessage msg, String time) {
                textMessage.setText(msg.message);
                textTime.setText(time);
            }
        }

        static class ReceivedViewHolder extends RecyclerView.ViewHolder {
            TextView textSenderName, textMessage, textTime;
            ReceivedViewHolder(View v) {
                super(v);
                textSenderName = v.findViewById(R.id.textSenderName);
                textMessage = v.findViewById(R.id.textMessage);
                textTime = v.findViewById(R.id.textTime);
            }
            void bind(ChatMessage msg, String time) {
                textSenderName.setText(msg.senderName);
                textMessage.setText(msg.message);
                textTime.setText(time);
            }
        }
    }
}
