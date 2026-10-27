package com.example.mobileproject.view;

import android.content.Intent;
import android.graphics.Color;
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

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MessagesFragment extends TabContentFragment {
    public MessagesFragment() { super(R.layout.activity_chat); }
    private final Map<String, DocumentSnapshot> conversations = new HashMap<>();
    private final Map<String, Profile> profiles = new HashMap<>();
    private final Map<String, ListenerRegistration> profileListeners = new HashMap<>();
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private ConversationAdapter adapter;
    private String userId = "";
    private String searchText = "";
    private TextView chipAll, chipUnread, chipRead;
    private int listenerGeneration;
    private int activeFilterMode = 0; // 0 = Tất cả, 1 = Chưa đọc, 2 = Đã đọc

    @Override
    public void onViewCreated(@androidx.annotation.NonNull View view, @androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViewById(R.id.btnChatBack).setOnClickListener(v -> backToExplore());
        if (savedInstanceState != null) {
            searchText = savedInstanceState.getString("messageSearch", "");
            activeFilterMode = savedInstanceState.getInt("messageFilter", 0);
        }
        RecyclerView list = findViewById(R.id.chatRecyclerView);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        chipAll = findViewById(R.id.chipAll);
        chipUnread = findViewById(R.id.chipUnread);
        chipRead = findViewById(R.id.chipRead);

        if (chipAll != null) chipAll.setOnClickListener(v -> selectFilter(0));
        if (chipUnread != null) chipUnread.setOnClickListener(v -> selectFilter(1));
        if (chipRead != null) chipRead.setOnClickListener(v -> selectFilter(2));

        search.setText(searchText);
        selectFilter(activeFilterMode);
    }

    private void selectFilter(int mode) {
        activeFilterMode = mode;
        setChipSelected(chipAll, mode == 0);
        setChipSelected(chipUnread, mode == 1);
        setChipSelected(chipRead, mode == 2);
        renderConversations();
    }

    private void setChipSelected(TextView chip, boolean selected) {
        if (chip == null) return;
        if (selected) {
            chip.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_selected));
            chip.setTextColor(Color.WHITE);
        } else {
            chip.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_unselected));
            chip.setTextColor(Color.parseColor("#444444"));
        }
    }



    @Override
    public void onResume() {
        super.onResume();
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

        final int generation = ++listenerGeneration;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        listeners.add(db.collection("conversations")
                .whereEqualTo("participant1Id", userId)
                .addSnapshotListener((snapshot, error) ->
                        onConversationSnapshot(snapshot, error, "participant1Id", generation)));
        listeners.add(db.collection("conversations")
                .whereEqualTo("participant2Id", userId)
                .addSnapshotListener((snapshot, error) ->
                        onConversationSnapshot(snapshot, error, "participant2Id", generation)));
    }

    private void onConversationSnapshot(QuerySnapshot snapshot, Exception error, String participantField, int generation) {
        if (!screenActive() || generation != listenerGeneration) return;
        if (error != null) {
            Toast.makeText(requireContext(), "Không đồng bộ được tin nhắn: " + error.getMessage(),
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

        final int generation = listenerGeneration;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        for (String participantId : participantIds) {
            if (profileListeners.containsKey(participantId)) continue;
            ListenerRegistration registration = db.collection("accounts")
                    .document(participantId)
                    .addSnapshotListener((document, error) -> {
                        if (!screenActive() || generation != listenerGeneration || error != null) return;
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

        com.example.mobileproject.util.BlockHelper.getBlockedUserIds(blockedIds -> {
            if (!screenActive()) return;

            int totalCount = 0;
            int unreadCount = 0;
            int readCount = 0;

            List<ConversationRow> rows = new ArrayList<>();
            for (DocumentSnapshot document : conversations.values()) {
                String firstId = document.getString("participant1Id");
                String secondId = document.getString("participant2Id");
                String otherId = userId.equals(firstId) ? secondId : firstId;
                if (otherId == null || otherId.trim().isEmpty()) continue;
                if (blockedIds.contains(otherId)) continue;

                totalCount++;
                String lastSender = document.getString("lastMessageSenderId");
                Boolean isReadObj = document.getBoolean("isRead");
                if (isReadObj == null) isReadObj = document.getBoolean("read");

                boolean isUnread = lastSender != null && !lastSender.equals(userId) && !Boolean.TRUE.equals(isReadObj);

                if (isUnread) unreadCount++;
                else readCount++;

                if (activeFilterMode == 1 && !isUnread) continue;
                if (activeFilterMode == 2 && isUnread) continue;

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

                if (lastSender != null && lastSender.equals(userId)) {
                    preview = "Bạn: " + preview;
                }

                Date msgDate = null;
                Object lastAt = document.get("lastMessageAt");
                if (lastAt instanceof com.google.firebase.Timestamp) msgDate = ((com.google.firebase.Timestamp) lastAt).toDate();
                else if (lastAt instanceof Date) msgDate = (Date) lastAt;

                String timeText = "";
                if (msgDate != null) {
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", new Locale("vi", "VN"));
                    timeText = timeFormat.format(msgDate);
                }

                if (!searchText.isEmpty()
                        && !name.toLowerCase(Locale.ROOT).contains(searchText)
                        && !preview.toLowerCase(Locale.ROOT).contains(searchText)) continue;
                String avatarUrl = profile == null ? null : profile.avatarUrl;
                rows.add(new ConversationRow(otherId, name, preview, avatarUrl, timeText, isUnread));
            }

            if (chipAll != null) chipAll.setText("Tất cả (" + totalCount + ")");
            if (chipUnread != null) chipUnread.setText("Chưa đọc (" + unreadCount + ")");
            if (chipRead != null) chipRead.setText("Đã đọc (" + readCount + ")");

            adapter.submit(rows);
        });
    }

    private void removeConversationListeners() {
        for (ListenerRegistration listener : listeners) listener.remove();
        listeners.clear();
    }

    private void showBlockDialogForPartner(String partnerId, String name) {
        if (userId.isEmpty() || partnerId == null || partnerId.isEmpty()) return;
        String myBlockDocId = userId + "_" + partnerId;

        FirebaseFirestore.getInstance().collection("blocks").document(myBlockDocId).get()
                .addOnSuccessListener(doc -> {
                    if (!screenActive()) return;
                    boolean isBlocked = doc.exists();
                    String actionText = isBlocked ? "Bỏ chặn " + name : "Chặn " + name;

                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle(name)
                            .setItems(new String[]{actionText}, (dialog, which) -> {
                                if (isBlocked) {
                                    FirebaseFirestore.getInstance().collection("blocks").document(myBlockDocId).delete()
                                            .addOnSuccessListener(aVoid -> {
                                                if (!screenActive()) return;
                                                Toast.makeText(requireContext(), "Đã bỏ chặn " + name, Toast.LENGTH_SHORT).show();
                                                renderConversations();
                                            });
                                } else {
                                    Map<String, Object> blockData = new HashMap<>();
                                    blockData.put("blockId", myBlockDocId);
                                    blockData.put("blockerId", userId);
                                    blockData.put("blockedUserId", partnerId);
                                    blockData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

                                    FirebaseFirestore.getInstance().collection("blocks").document(myBlockDocId).set(blockData)
                                            .addOnSuccessListener(aVoid -> {
                                                if (!screenActive()) return;
                                                Toast.makeText(requireContext(), "Đã chặn " + name, Toast.LENGTH_SHORT).show();
                                                renderConversations();
                                            });
                                }
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
    }

    private void removeProfileListeners() {
        for (ListenerRegistration listener : profileListeners.values()) listener.remove();
        profileListeners.clear();
    }

    @Override
    public void onPause() {
        listenerGeneration++;
        removeConversationListeners();
        removeProfileListeners();
        super.onPause();
    }

    @Override public void onSaveInstanceState(@androidx.annotation.NonNull Bundle out) {
        out.putString("messageSearch", searchText);
        out.putInt("messageFilter", activeFilterMode);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroyView() {
        removeConversationListeners(); removeProfileListeners();
        ((RecyclerView) findViewById(R.id.chatRecyclerView)).setAdapter(null);
        adapter = null;
        chipAll = chipUnread = chipRead = null;
        super.onDestroyView();
    }

    private static class Profile {
        String name;
        String avatarUrl;
    }

    private static class ConversationRow {
        final String otherId;
        final String name;
        final String preview;
        final String avatarUrl;
        final String timeText;
        final boolean isUnread;

        ConversationRow(String otherId, String name, String preview, String avatarUrl, String timeText, boolean isUnread) {
            this.otherId = otherId;
            this.name = name;
            this.preview = preview;
            this.avatarUrl = avatarUrl;
            this.timeText = timeText;
            this.isUnread = isUnread;
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
            if (holder.time != null) holder.time.setText(row.timeText);
            holder.initials.setText(initials(row.name));

            if (row.isUnread) {
                holder.name.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.preview.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.preview.setTextColor(Color.parseColor("#10132D"));
            } else {
                holder.name.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.preview.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.preview.setTextColor(Color.parseColor("#8289A4"));
            }

            Glide.with(holder.photo).clear(holder.photo);
            if (row.avatarUrl == null || row.avatarUrl.trim().isEmpty()) {
                holder.photo.setVisibility(View.GONE);
            } else {
                holder.photo.setVisibility(View.VISIBLE);
                Glide.with(holder.photo).load(row.avatarUrl).circleCrop().into(holder.photo);
            }
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ChatDetailActivity.class);
                intent.putExtra(ChatDetailActivity.EXTRA_RECIPIENT_ID, row.otherId);
                intent.putExtra(ChatDetailActivity.EXTRA_PARTNER_NAME, row.name);
                intent.putExtra(ChatDetailActivity.EXTRA_PARTNER_AVATAR, row.avatarUrl);
                startActivity(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                showBlockDialogForPartner(row.otherId, row.name);
                return true;
            });
        }

        @Override public int getItemCount() { return rows.size(); }

        class RowHolder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView preview;
            final TextView time;
            final TextView initials;
            final ImageView photo;
            RowHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.chatProfileName);
                preview = itemView.findViewById(R.id.chatConversationPreview);
                time = itemView.findViewById(R.id.chatConversationTime);
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
