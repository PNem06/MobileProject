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

    public void updateExchange(android.content.Context context, String id, Map<String,Object> changes, UpdateCallback callback) {
        updateExchange(context, "exchanges", id, changes, callback);
    }
    public void updateExchange(android.content.Context context, String source, String id, Map<String,Object> changes, UpdateCallback callback) {
        if (id == null || id.trim().isEmpty()) { callback.onComplete(new Exception("Thiếu mã yêu cầu.")); return; }
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("exchangeId", id); body.put("collection", source); body.put("updates", new org.json.JSONObject(changes));
            com.example.mobileproject.wallet.WalletApi.call(context, "/v1/exchanges/update", body, (data,error) -> callback.onComplete(error));
        } catch (Exception error) { callback.onComplete(error); }
    }

    public Map<String, Object> statusUpdate(String status) {
        Map<String, Object> map = new HashMap<>(); map.put("status", status); return map;
    }
}
