package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mobileproject.R;
import com.example.mobileproject.adapter.BlockedUserAdapter;
import com.example.mobileproject.model.BlockedUserItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BlockedUsersActivity extends AppCompatActivity {

    private ImageView btnBack;
    private RecyclerView recyclerBlockedUsers;
    private LinearLayout layoutEmpty;

    private BlockedUserAdapter adapter;
    private final List<BlockedUserItem> blockedList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        db = FirebaseFirestore.getInstance();

        initViews();
        loadBlockedUsers();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        recyclerBlockedUsers = findViewById(R.id.recyclerBlockedUsers);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        recyclerBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockedUserAdapter(this, blockedList, item -> unblockUser(item));
        recyclerBlockedUsers.setAdapter(adapter);
    }

    private void loadBlockedUsers() {
        if (currentUid.isEmpty()) {
            showEmpty(true);
            return;
        }

        db.collection("blocks")
                .whereEqualTo("blockerId", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    blockedList.clear();
                    if (snapshot.isEmpty()) {
                        showEmpty(true);
                        return;
                    }

                    List<String> blockedUserIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String blockedUserId = doc.getString("blockedUserId");
                        Date createdAt = doc.getDate("createdAt");
                        if (blockedUserId != null && !blockedUserId.isEmpty()) {
                            BlockedUserItem item = new BlockedUserItem(blockedUserId, "", "", createdAt);
                            blockedList.add(item);
                            blockedUserIds.add(blockedUserId);
                        }
                    }

                    if (blockedList.isEmpty()) {
                        showEmpty(true);
                        return;
                    }

                    fetchUserProfiles();
                })
                .addOnFailureListener(e -> {
                    showEmpty(true);
                    Toast.makeText(this, "Lỗi tải danh sách chặn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchUserProfiles() {
        if (blockedList.isEmpty()) {
            showEmpty(true);
            return;
        }

        final int[] loadedCount = {0};
        for (BlockedUserItem item : blockedList) {
            db.collection("accounts").document(item.blockedUserId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            item.fullName = doc.getString("fullName");
                            item.avatarUrl = doc.getString("avatarUrl");
                        }
                        if (item.fullName == null || item.fullName.trim().isEmpty()) {
                            item.fullName = item.blockedUserId;
                        }
                        loadedCount[0]++;
                        if (loadedCount[0] == blockedList.size()) {
                            showEmpty(false);
                            adapter.notifyDataSetChanged();
                        }
                    })
                    .addOnFailureListener(e -> {
                        item.fullName = item.blockedUserId;
                        loadedCount[0]++;
                        if (loadedCount[0] == blockedList.size()) {
                            showEmpty(false);
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }

    private void unblockUser(BlockedUserItem item) {
        String docId = currentUid + "_" + item.blockedUserId;
        db.collection("blocks").document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã bỏ chặn " + (item.fullName != null && !item.fullName.isEmpty() ? item.fullName : "người dùng"), Toast.LENGTH_SHORT).show();
                    blockedList.remove(item);
                    adapter.notifyDataSetChanged();
                    showEmpty(blockedList.isEmpty());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Bỏ chặn thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showEmpty(boolean isEmpty) {
        if (isEmpty) {
            recyclerBlockedUsers.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            recyclerBlockedUsers.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }
}
