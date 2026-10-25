package com.example.mobileproject.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** Truy vấn exchanges thật; accountId được lấy từ document accounts/{Firebase UID}. */
public class ExchangeRepository {
    public interface AccountIdCallback { void onResult(String accountId, Exception error); }
    public interface UpdateCallback { void onComplete(Exception error); }
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void resolveCurrentAccountId(AccountIdCallback callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onResult(null, new Exception("Bạn chưa đăng nhập Firebase."));
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection("accounts").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) {
                // Một số dữ liệu cũ dùng accountId làm document ID. Chỉ dùng UID nếu document đó tồn tại.
                db.collection("accounts").whereEqualTo("googleUid", uid).limit(1).get()
                        .addOnSuccessListener(q -> {
                            if (q.isEmpty()) callback.onResult(null, new Exception("Không tìm thấy tài khoản trong accounts cho UID đăng nhập."));
                            else callback.onResult(accountId(q.getDocuments().get(0)), null);
                        }).addOnFailureListener(e -> callback.onResult(null, e));
            } else callback.onResult(accountId(doc), null);
        }).addOnFailureListener(e -> callback.onResult(null, e));
    }

    private String accountId(DocumentSnapshot doc) {
        String id = doc.getString("accountId");
        return id == null || id.trim().isEmpty() ? doc.getId() : id;
    }

    public void updateExchange(String exchangeId, Map<String, Object> updates, UpdateCallback callback) {
        if (exchangeId == null || exchangeId.trim().isEmpty()) {
            callback.onComplete(new Exception("Thiếu exchangeId.")); return;
        }
        updates.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        db.collection("exchanges").document(exchangeId).get().addOnSuccessListener(doc -> {
            if (Boolean.TRUE.equals(doc.getBoolean("walletManaged"))) {
                callback.onComplete(new Exception("Giao dịch đã thanh toán bằng ví phải xử lý tại màn chi tiết thanh toán."));
                return;
            }
            db.collection("exchanges").document(exchangeId).update(updates)
                    .addOnSuccessListener(unused -> callback.onComplete(null))
                    .addOnFailureListener(callback::onComplete);
        }).addOnFailureListener(callback::onComplete);
    }

    public Map<String, Object> statusUpdate(String status) {
        Map<String, Object> map = new HashMap<>(); map.put("status", status); return map;
    }
}
