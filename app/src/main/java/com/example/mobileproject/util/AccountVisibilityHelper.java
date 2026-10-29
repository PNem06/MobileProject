package com.example.mobileproject.util;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashSet;
import java.util.Set;

/** Resolves every identifier used for accounts currently locked by moderation. */
public final class AccountVisibilityHelper {
    public interface Listener { void onLoaded(Set<String> accountIds); }

    private AccountVisibilityHelper() { }

    public static void getLockedAccountIds(Listener listener) {
        FirebaseFirestore.getInstance().collection("accounts")
                .whereEqualTo("status", "LOCKED")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> ids = new HashSet<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ids.add(doc.getId());
                        add(ids, doc.getString("accountId"));
                        add(ids, doc.getString("authUid"));
                        add(ids, doc.getString("googleUid"));
                    }
                    if (listener != null) listener.onLoaded(ids);
                })
                .addOnFailureListener(error -> {
                    if (listener != null) listener.onLoaded(new HashSet<>());
                });
    }

    private static void add(Set<String> ids, String value) {
        if (value != null && !value.trim().isEmpty()) ids.add(value.trim());
    }
}
