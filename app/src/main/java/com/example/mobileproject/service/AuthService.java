package com.example.mobileproject.service;

import com.example.mobileproject.model.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AuthService {
    public interface Callback {
        void onSuccess(String role);
        void onError(String message);
    }
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    public AuthService() {
    }
    public void registerWithPassword(String username, String password, String email, String fullName, String phone, Callback callback) {
        username = username.trim().toLowerCase(Locale.ROOT);
        email = email.trim();
        fullName = fullName.trim();
        phone = phone.trim();
        if (!username.matches("[a-z0-9_]{3,24}") || fullName.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() || password.length() < 6) {
            callback.onError("Kiểm tra họ tên, username (3–24 ký tự a-z, 0-9, _), email và mật khẩu (ít nhất 6 ký tự).");
            return;
        }
        final String finalUsername = username;
        final String finalEmail = email;
        final String finalFullName = fullName;
        final String finalPhone = phone;
        // TODO: Đảm bảo username duy nhất bằng backend/transaction tin cậy trước khi phát hành.
        db.collection("accounts").whereEqualTo("username", finalUsername).limit(1).get()
                .addOnSuccessListener(found -> {
                    if (!found.isEmpty()) { callback.onError("Username đã được sử dụng."); return; }
                    auth.createUserWithEmailAndPassword(finalEmail, password)
                            .addOnSuccessListener(result -> {
                                FirebaseUser user = result.getUser();
                                if (user == null) { callback.onError("Không nhận được tài khoản Firebase."); return; }
                                Map<String,Object> profile = new HashMap<>();
                                profile.put("accountId", user.getUid());
                                profile.put("username", finalUsername);
                                profile.put("fullName", finalFullName);
                                profile.put("email", finalEmail);
                                profile.put("phone", finalPhone);
                                profile.put("avatarUrl", "");
                                profile.put("googleUid", null);
                                profile.put("rating", 0.0);
                                profile.put("role", "USER");
                                profile.put("status", "ACTIVE");
                                profile.put("totalExchanges", 0);
                                // Không ghi passwordHash: Firebase Auth quản lý mật khẩu.
                                db.collection("accounts").document(user.getUid()).set(profile)
                                        .addOnSuccessListener(unused -> { auth.signOut(); callback.onSuccess("USER"); })
                                        .addOnFailureListener(e -> user.delete().addOnCompleteListener(t -> {
                                            auth.signOut();
                                            callback.onError("Không lưu được hồ sơ; kiểm tra Firestore Rules. " + e.getMessage());
                                        }));
                            }).addOnFailureListener(e -> callback.onError("Đăng ký thất bại: " + e.getMessage()));
                }).addOnFailureListener(e -> callback.onError("Không kiểm tra được username: " + e.getMessage()));
    }
    public void signInWithPassword(String usernameOrEmail, String password, Callback callback) {
        String input = usernameOrEmail.trim();
        if (input.isEmpty() || password.isEmpty()) {
            callback.onError("Vui lòng nhập tên đăng nhập hoặc email và mật khẩu.");
            return;
        }
        // Trường hợp 1: Người dùng nhập Email
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            loginByEmail(input, password, callback);
            return;
        }
        // Trường hợp 2: Người dùng nhập Username
        String username = input.toLowerCase(Locale.ROOT);
        db.collection("accounts")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onError("Tên đăng nhập hoặc mật khẩu không chính xác.");
                        return;
                    }
                    DocumentSnapshot document = query.getDocuments().get(0);
                    String email = document.getString("email");
                    if (email == null || email.trim().isEmpty()) {
                        callback.onError("Tài khoản chưa có email đăng nhập hợp lệ.");
                        return;
                    }
                    // Dùng email tìm được để Firebase kiểm tra mật khẩu
                    loginByEmail(email, password, callback);
                })
                .addOnFailureListener(e -> {
                    callback.onError("Không thể tra cứu tên đăng nhập: " + e.getMessage());
                });
    }
    private void loginByEmail(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> loadProfile(callback))
                .addOnFailureListener(e -> {
                    callback.onError(
                            "Lỗi đăng nhập Firebase: "
                                    + e.getClass().getSimpleName()
                                    + " - " + e.getMessage()
                    );
                });
    }
    public void signInWithGoogle(String idToken, Callback callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) { callback.onError("Không nhận được tài khoản Google."); return; }
                    db.collection("accounts").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) { validateProfile(doc, callback); return; }
                                // Chỉ tự tạo hồ sơ USER cho tài khoản Google chưa tồn tại.
                                Map<String,Object> profile = new HashMap<>();
                                profile.put("accountId", user.getUid());
                                profile.put("username", "google_" + user.getUid());
                                profile.put("fullName", user.getDisplayName() == null ? "" : user.getDisplayName());
                                profile.put("email", user.getEmail() == null ? "" : user.getEmail());
                                profile.put("phone", "");
                                profile.put("avatarUrl", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
                                profile.put("googleUid", user.getUid());
                                profile.put("rating", 0.0);
                                profile.put("role", "USER");
                                profile.put("status", "ACTIVE");
                                profile.put("totalExchanges", 0);
                                db.collection("accounts").document(user.getUid()).set(profile)
                                        .addOnSuccessListener(unused -> loadProfile(callback))
                                        .addOnFailureListener(e -> {
                                            auth.signOut();
                                            callback.onError("Không tạo được hồ sơ Google: " + e.getMessage());
                                        });
                            }).addOnFailureListener(e -> { auth.signOut(); callback.onError(e.getMessage()); });
                }).addOnFailureListener(e -> callback.onError("Google Sign-In thất bại: " + e.getMessage()));
    }
    public void loadProfile(Callback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { callback.onError("Bạn chưa đăng nhập."); return; }
        db.collection("accounts").document(user.getUid()).get()
                .addOnSuccessListener(doc -> validateProfile(doc, callback))
                .addOnFailureListener(e -> { auth.signOut(); callback.onError("Không đọc được hồ sơ: " + e.getMessage()); });
    }
    private void validateProfile(DocumentSnapshot doc, Callback callback) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null || !doc.getId().equals(current.getUid())) {
            callback.onError("Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.");
            return;
        }
        if (!doc.exists()) { auth.signOut(); callback.onError("Tài khoản chưa có hồ sơ accounts tương ứng."); return; }
        String status = doc.getString("status");
        String role = doc.getString("role");
        if (!"ACTIVE".equals(status)) {
            auth.signOut();
            if ("LOCKED".equals(status)) {
                callback.onError("Tài khoản bạn đã bị khóa.");
            } else {
                callback.onError("Tài khoản chưa được kích hoạt hoặc không hoạt động.");
            }
            return;
        }
        if (!"USER".equals(role) && !"ADMIN".equals(role)) { auth.signOut(); callback.onError("Vai trò tài khoản không hợp lệ."); return; }
        com.example.mobileproject.util.GlobalNotificationListener.onLoginValidated(current.getUid());
        callback.onSuccess(role);
    }
    public void signOut() {
        auth.signOut();
    }
}
