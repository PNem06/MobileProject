package com.example.mobileproject.util;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockHelper {

    public interface OnBlockedUsersLoadedListener {
        void onBlockedUsersLoaded(Set<String> blockedUserIds);
    }

    public static void getBlockedUserIds(OnBlockedUsersLoadedListener listener) {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
        if (currentUid.isEmpty()) {
            if (listener != null) listener.onBlockedUsersLoaded(new HashSet<>());
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Set<String> blockedIds = new HashSet<>();

        db.collection("blocks")
                .whereEqualTo("blockerId", currentUid)
                .get()
                .addOnSuccessListener(mineSnap -> {
                    for (QueryDocumentSnapshot doc : mineSnap) {
                        String blockedUserId = doc.getString("blockedUserId");
                        if (blockedUserId != null && !blockedUserId.isEmpty()) {
                            blockedIds.add(blockedUserId);
                        }
                    }

                    db.collection("blocks")
                            .whereEqualTo("blockedUserId", currentUid)
                            .get()
                            .addOnSuccessListener(theirsSnap -> {
                                for (QueryDocumentSnapshot doc : theirsSnap) {
                                    String blockerId = doc.getString("blockerId");
                                    if (blockerId != null && !blockerId.isEmpty()) {
                                        blockedIds.add(blockerId);
                                    }
                                }
                                if (listener != null) listener.onBlockedUsersLoaded(blockedIds);
                            })
                            .addOnFailureListener(e -> {
                                if (listener != null) listener.onBlockedUsersLoaded(blockedIds);
                            });
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onBlockedUsersLoaded(new HashSet<>());
                });
    }

    public static void showBlockDialog(Context context, String targetUserId, String targetUserName) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        if (currentUid == null || currentUid.isEmpty() || targetUserId == null || targetUserId.isEmpty() || currentUid.equals(targetUserId)) {
            return;
        }

        String docId = currentUid + "_" + targetUserId;
        FirebaseFirestore.getInstance().collection("blocks").document(docId).get()
                .addOnSuccessListener(doc -> {
                    boolean isBlocked = doc.exists();
                    String actionText = isBlocked ? "Bỏ chặn " + targetUserName : "Chặn " + targetUserName;

                    new AlertDialog.Builder(context)
                            .setTitle(targetUserName)
                            .setItems(new String[]{actionText}, (dialog, which) -> {
                                if (isBlocked) {
                                    FirebaseFirestore.getInstance().collection("blocks").document(docId).delete()
                                            .addOnSuccessListener(aVoid -> Toast.makeText(context, "Đã bỏ chặn " + targetUserName, Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e -> Toast.makeText(context, "Bỏ chặn thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                } else {
                                    Map<String, Object> blockData = new HashMap<>();
                                    blockData.put("blockId", docId);
                                    blockData.put("blockerId", currentUid);
                                    blockData.put("blockedUserId", targetUserId);
                                    blockData.put("createdAt", FieldValue.serverTimestamp());

                                    FirebaseFirestore.getInstance().collection("blocks").document(docId).set(blockData)
                                            .addOnSuccessListener(aVoid -> Toast.makeText(context, "Đã chặn " + targetUserName, Toast.LENGTH_SHORT).show())
                                            .addOnFailureListener(e -> Toast.makeText(context, "Chặn thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
    }
}
