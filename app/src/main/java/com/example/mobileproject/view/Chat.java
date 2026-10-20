package com.example.mobileproject.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.MainActivity;
import com.example.mobileproject.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Chat extends AppCompatActivity {
    private final Map<String, DocumentSnapshot> conversations = new HashMap<>();
    private final Map<String, Profile> profiles = new HashMap<>();
    private final Map<String, ListenerRegistration> profileListeners = new HashMap<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private ConversationAdapter adapter;
    private String userId = "";
    private String searchText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);

        View root = findViewById(R.id.chatRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);

        findViewById(R.id.btnChatBack).setOnClickListener(
                v -> getOnBackPressedDispatcher().onBackPressed());

        RecyclerView list = findViewById(R.id.chatRecyclerView);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConversationAdapter();
        list.setAdapter(adapter);

        EditText search = findViewById(R.id.chatSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchText = s.toString().trim().toLowerCase(Locale.ROOT);
                renderConversations();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        setupBottomNavigation();
        BottomNavigationView navigation = findViewById(R.id.chatBottomMenu);
        navigation.getMenu().findItem(R.id.menu_message).setChecked(true);
    }

    private void setupBottomNavigation() {
        BottomNavigationView navigation = findViewById(R.id.chatBottomMenu);
        navigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Intent intent;
            if (id == R.id.menu_message) return true;
            if (id == R.id.menu_request) {
                intent = new Intent(this, Request.class);
            } else if (id == R.id.menu_calendar) {
                intent = new Intent(this, ExchangeScheduleActivity.class);
            } else if (id == R.id.menu_explore
                    || id == R.id.menu_settings) {
                intent = new Intent(this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_HOME_PAGE,
                        id == R.id.menu_settings ? "account" : "home");
            } else {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachConversationListeners();
    }

    private void attachConversationListeners() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String signedInUserId = user == null ? null : user.getUid();
        if (signedInUserId == null || signedInUserId.isEmpty()) {
            removeConversationListeners();
            removeProfileListeners();
            conversations.clear();
            profiles.clear();
            renderConversations();
            return;
        }
        if (signedInUserId.equals(userId) && !listeners.isEmpty()) return;
        removeConversationListeners();
        removeProfileListeners();
        profiles.clear();
        userId = signedInUserId;
        conversations.clear();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        listeners.add(db.collection("conversations")
                .whereEqualTo("participant1Id", userId)
                .addSnapshotListener((snapshot, error) ->
                        onConversationSnapshot(snapshot, error, "participant1Id")));
        listeners.add(db.collection("conversations")
                .whereEqualTo("participant2Id", userId)
                .addSnapshotListener((snapshot, error) ->
                        onConversationSnapshot(snapshot, error, "participant2Id")));
    }

    private void onConversationSnapshot(QuerySnapshot snapshot, Exception error, String participantField) {
        if (error != null) {
            Toast.makeText(this, "Không đồng bộ được tin nhắn: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (snapshot == null) return;
        Iterator<Map.Entry<String, DocumentSnapshot>> iterator =
                conversations.entrySet().iterator();
        while (iterator.hasNext()) {
            DocumentSnapshot current = iterator.next().getValue();
            if (userId.equals(current.getString(participantField))) iterator.remove();
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            conversations.put(document.getId(), document);
        }
        syncProfileListeners();
        renderConversations();
    }

    private void syncProfileListeners() {
        Set<String> participantIds = new HashSet<>();
        for (DocumentSnapshot conversation : conversations.values()) {
            String first = conversation.getString("participant1Id");
            String second = conversation.getString("participant2Id");
            String other = userId.equals(first) ? second : first;
            if (other != null && !other.trim().isEmpty()) participantIds.add(other);
        }

        Iterator<Map.Entry<String, ListenerRegistration>> iterator =
                profileListeners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = iterator.next();
            if (!participantIds.contains(entry.getKey())) {
                entry.getValue().remove();
                profiles.remove(entry.getKey());
                iterator.remove();
            }
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        for (String participantId : participantIds) {
            if (profileListeners.containsKey(participantId)) continue;
            ListenerRegistration registration = db.collection("accounts")
                    .document(participantId)
                    .addSnapshotListener((document, error) -> {
                        if (error != null) return;
                        Profile profile = new Profile();
                        if (document != null && document.exists()) {
                            profile.name = document.getString("fullName");
                            profile.avatarUrl = document.getString("avatarUrl");
                        }
                        profiles.put(participantId, profile);
                        renderConversations();
                    });
            profileListeners.put(participantId, registration);
        }
    }

    private void renderConversations() {
        if (adapter == null) return;
        List<ConversationRow> rows = new ArrayList<>();
        for (DocumentSnapshot document : conversations.values()) {
            String firstId = document.getString("participant1Id");
            String secondId = document.getString("participant2Id");
            String otherId = userId.equals(firstId) ? secondId : firstId;
            if (otherId == null || otherId.trim().isEmpty()) continue;

            Profile profile = profiles.get(otherId);
            String name = profile == null ? null : profile.name;
            if (name == null || name.trim().isEmpty()) name = document.getString("otherParticipantName");
            if (name == null || name.trim().isEmpty()) {
                name = userId.equals(firstId)
                        ? document.getString("participant2Name")
                        : document.getString("participant1Name");
            }
            if (name == null || name.trim().isEmpty()) name = otherId;
            String preview = document.getString("lastMessage");
            if (preview == null || preview.trim().isEmpty()) preview = "Bắt đầu trò chuyện";
            if (!searchText.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(searchText)
                    && !preview.toLowerCase(Locale.ROOT).contains(searchText)) continue;
            String avatarUrl = profile == null ? null : profile.avatarUrl;
            rows.add(new ConversationRow(name, preview, avatarUrl));
        }
        adapter.submit(rows);
    }

    private void removeConversationListeners() {
        for (ListenerRegistration listener : listeners) listener.remove();
        listeners.clear();
    }

    private void removeProfileListeners() {
        for (ListenerRegistration listener : profileListeners.values()) listener.remove();
        profileListeners.clear();
    }

    @Override
    protected void onStop() {
        removeConversationListeners();
        removeProfileListeners();
        super.onStop();
    }

    private static class Profile {
        String name;
        String avatarUrl;
    }

    private static class ConversationRow {
        final String name;
        final String preview;
        final String avatarUrl;
        ConversationRow(String name, String preview, String avatarUrl) {
            this.name = name;
            this.preview = preview;
            this.avatarUrl = avatarUrl;
        }
    }

    private class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.RowHolder> {
        private final List<ConversationRow> rows = new ArrayList<>();

        void submit(List<ConversationRow> updated) {
            rows.clear();
            rows.addAll(updated);
            notifyDataSetChanged();
        }

        @Override
        public RowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_conversation, parent, false);
            return new RowHolder(row);
        }

        @Override
        public void onBindViewHolder(RowHolder holder, int position) {
            ConversationRow row = rows.get(position);
            holder.name.setText(row.name);
            holder.preview.setText(row.preview);
            holder.initials.setText(initials(row.name));
            Glide.with(holder.photo).clear(holder.photo);
            if (row.avatarUrl == null || row.avatarUrl.trim().isEmpty()) {
                holder.photo.setVisibility(View.GONE);
            } else {
                holder.photo.setVisibility(View.VISIBLE);
                Glide.with(holder.photo).load(row.avatarUrl).circleCrop().into(holder.photo);
            }
        }

        @Override public int getItemCount() { return rows.size(); }

        class RowHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView preview;
            final TextView initials;
            final ImageView photo;
            RowHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.chatProfileName);
                preview = itemView.findViewById(R.id.chatConversationPreview);
                initials = itemView.findViewById(R.id.chatProfileInitials);
                photo = itemView.findViewById(R.id.chatProfilePhoto);
            }
        }
    }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                .toUpperCase(Locale.ROOT);
    }
}
