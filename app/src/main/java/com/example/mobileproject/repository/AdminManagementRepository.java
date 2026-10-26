package com.example.mobileproject.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.mobileproject.data.AdminCacheDao;
import com.example.mobileproject.data.AdminLocalDatabase;
import com.example.mobileproject.data.ExchangeCacheEntity;
import com.example.mobileproject.data.ReportCacheEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminManagementRepository {
    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final AdminCacheDao dao;
    private final Application application;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ListenerRegistration reportListener;
    private ListenerRegistration exchangeListener;
    private ListenerRegistration paymentListener;
    private ListenerRegistration accountListener;
    private List<DocumentSnapshot> latestAccounts = new ArrayList<>();
    private List<DocumentSnapshot> latestExchanges = new ArrayList<>();
    private List<DocumentSnapshot> latestPayments = new ArrayList<>();

    public AdminManagementRepository(Application application) {
        this.application=application;
        dao = AdminLocalDatabase.getInstance(application).adminCacheDao();
    }

    public LiveData<List<ReportCacheEntity.AdminReportCacheEntity>> observeReports() { return dao.observeReports(); }
    public LiveData<List<ExchangeCacheEntity>> observeExchanges() { return dao.observeExchanges(); }

    public void startReportListening() {
        if (reportListener != null) return;

        reportListener = firestore.collection("reports").addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) return;

            List<ReportCacheEntity.AdminReportCacheEntity> result = new ArrayList<>();

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                ReportCacheEntity.AdminReportCacheEntity item = new ReportCacheEntity.AdminReportCacheEntity();
                item.reportId = doc.getId();
                item.reporterId = firstString(
                        doc, "reporterId", "reporter_accountId", "reporterAccountId"
                );

                // Phải xác định targetType trước khi chọn targetId.
                item.targetType = firstString(doc, "targetType", "target_type").toUpperCase();

                if (item.targetType.isEmpty()) {
                    if (hasAny(doc, "target_postId", "targetPostId")) {
                        item.targetType = "POST";
                    } else if (hasAny(doc, "target_accountId", "targetAccountId")) {
                        item.targetType = "ACCOUNT";
                    }
                }

                if ("ACCOUNT".equalsIgnoreCase(item.targetType)
                        || "USER".equalsIgnoreCase(item.targetType)) {
                    item.targetId = firstString(
                            doc, "target_accountId", "targetAccountId", "targetId"
                    );
                } else if ("POST".equalsIgnoreCase(item.targetType)) {
                    // Với report cũ không có target_postId, giữ target_accountId
                    // làm giá trị dự phòng để thử tìm bài đăng thuộc tài khoản đó.
                    item.targetId = firstString(
                            doc, "target_postId", "targetPostId", "targetId",
                            "target_accountId", "targetAccountId"
                    );
                } else {
                    item.targetId = firstString(
                            doc, "targetId", "target_accountId", "targetAccountId",
                            "target_postId", "targetPostId"
                    );
                }

                item.targetName = firstString(doc, "targetName", "targetTitle", "target_name");
                item.reason = firstString(doc, "reason", "reportReason");
                item.description = firstString(doc, "description", "detail");
                item.status = firstString(doc, "status");
                if (item.status.isEmpty()) item.status = "PENDING";
                item.updatedAt = dateMillis(doc.get("updatedAt"));
                result.add(item);
            }

            io.execute(() -> dao.replaceReports(result));
        });
    }

    // Callback nội bộ trả về bài đăng tìm được.
    private interface PostLookupCallback {
        void onFound(DocumentSnapshot post);
        void onError(String message);
    }

    // Tìm bài đăng theo Document ID, postId, rồi theo tài khoản sở hữu.
    // Nếu tài khoản có nhiều bài đăng mà report không lưu postId, không tự chọn bừa.
    private void findPostForReport(String targetId, PostLookupCallback callback) {
        firestore.collection("posts").document(targetId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        callback.onFound(document);
                        return;
                    }

                    firestore.collection("posts")
                            .whereEqualTo("postId", targetId)
                            .limit(2)
                            .get()
                            .addOnSuccessListener(query -> {
                                if (!query.isEmpty()) {
                                    if (query.size() == 1) {
                                        callback.onFound(query.getDocuments().get(0));
                                    } else {
                                        callback.onError(
                                                "Có nhiều bài đăng trùng postId. Hãy kiểm tra dữ liệu posts."
                                        );
                                    }
                                    return;
                                }

                                // Dữ liệu report cũ có thể chỉ lưu target_accountId.
                                findPostByOwnerField(
                                        targetId,
                                        new String[]{"accountId", "ownerId", "userId", "owner_accountId"},
                                        0,
                                        callback
                                );
                            })
                            .addOnFailureListener(e ->
                                    callback.onError("Không đọc được bài đăng: " + e.getMessage())
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError("Không đọc được bài đăng: " + e.getMessage())
                );
    }

    private void findPostByOwnerField(
            String ownerId,
            String[] fields,
            int index,
            PostLookupCallback callback
    ) {
        if (index >= fields.length) {
            callback.onError(
                    "Không tìm thấy bài đăng. Report có thể chỉ lưu target_accountId, " +
                            "hoặc trường chủ bài đăng trong posts có tên khác."
            );
            return;
        }

        firestore.collection("posts")
                .whereEqualTo(fields[index], ownerId)
                .limit(2)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        findPostByOwnerField(ownerId, fields, index + 1, callback);
                    } else if (query.size() == 1) {
                        callback.onFound(query.getDocuments().get(0));
                    } else {
                        callback.onError(
                                "Report chỉ lưu mã tài khoản " + ownerId +
                                        " nhưng tài khoản này có nhiều bài đăng. " +
                                        "Cần lưu target_postId để xác định đúng bài bị báo cáo."
                        );
                    }
                })
                .addOnFailureListener(e ->
                        callback.onError("Không thể tìm bài đăng theo " + fields[index] +
                                ": " + e.getMessage())
                );
    }

    public void stopReportListening() {
        if (reportListener != null) { reportListener.remove(); reportListener = null; }
    }

    public void startExchangeListening() {
        if (exchangeListener == null) {
            exchangeListener = firestore.collection("exchanges").addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null) return;
                latestExchanges = new ArrayList<>(snapshot.getDocuments());
                syncExchangeCache();
            });
        }
        if (paymentListener == null) {
            paymentListener = firestore.collection("payments").addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null) return;
                latestPayments = new ArrayList<>(snapshot.getDocuments());
                syncExchangeCache();
            });
        }
        if (accountListener == null) {
            accountListener = firestore.collection("accounts")
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) return;

                        latestAccounts = new ArrayList<>(snapshot.getDocuments());
                        syncExchangeCache();
                    });
        }
    }

    public void stopExchangeListening() {
        if (exchangeListener != null) { exchangeListener.remove(); exchangeListener = null; }
        if (paymentListener != null) { paymentListener.remove(); paymentListener = null; }
        if (accountListener != null) {accountListener.remove();accountListener = null;}
    }

    private void syncExchangeCache() {
        Map<String, DocumentSnapshot> paymentByExchange = new HashMap<>();
        for (DocumentSnapshot payment : latestPayments) {
            String exchangeId = firstString(payment, "exchangeId", "exchange_id");
            if (!exchangeId.isEmpty()) paymentByExchange.put(exchangeId, payment);
        }
        List<ExchangeCacheEntity> result = new ArrayList<>();
        Map<String, DocumentSnapshot> accountById = new HashMap<>();

        for (DocumentSnapshot account : latestAccounts) {
            accountById.put(account.getId(), account);
            for (String field : new String[]{"accountId","authUid","googleUid"}) {
                String alias=firstString(account,field); if(!alias.isEmpty()) accountById.put(alias,account);
            }
        }
        for (DocumentSnapshot doc : latestExchanges) {
            ExchangeCacheEntity item = new ExchangeCacheEntity();
            item.exchangeId = doc.getId();
            item.requesterId = firstString(doc, "requesterId", "requester_accountId", "requesterAccountId");
            item.hostId = firstString(doc, "hostId", "host_accountId", "hostAccountId");
            DocumentSnapshot requester = accountById.get(item.requesterId);
            DocumentSnapshot host = accountById.get(item.hostId);

            if (requester != null) {
                item.requesterUsername = firstString(
                        requester, "username", "userName", "displayName", "fullName", "name"
                );
            }

            if (host != null) {
                item.hostUsername = firstString(
                        host, "username", "userName", "displayName", "fullName", "name"
                );
            }
            item.postId = firstString(doc, "postId", "post_id");
            item.exchangeType = firstString(doc, "exchangeType", "type");
            item.status = firstString(doc, "status");
            item.cancelReason = firstString(doc, "cancelReason", "cancel_reason");
            item.cancelRequestStatus = firstString(doc, "cancelRequestStatus", "cancellationStatus", "cancelStatus");
            if (item.cancelRequestStatus.isEmpty() && "CANCEL_REQUESTED".equalsIgnoreCase(item.status)) item.cancelRequestStatus = "PENDING";
            item.guestCount = intValue(doc.get("guestCount"));
            item.note = firstString(doc, "note");
            item.startDate = dateMillis(doc.get("startDate"));
            item.endDate = dateMillis(doc.get("endDate"));
            item.createdAt = dateMillis(doc.get("createdAt"));
            item.walletManaged=Boolean.TRUE.equals(doc.getBoolean("walletManaged"));
            DocumentSnapshot payment=paymentByExchange.get(item.exchangeId);
            java.util.Map<String,Object> legacy=payment==null?null:payment.getData();
            item.paymentStatus=com.example.mobileproject.data.ExchangePaymentMapper.status(item.walletManaged,doc.getData(),legacy);
            item.paymentAmount=com.example.mobileproject.data.ExchangePaymentMapper.amount(item.walletManaged,doc.getData(),legacy);
            item.paidAt=dateMillis(item.walletManaged?doc.get("paidAt"):payment==null?null:payment.get("paidAt"));
            result.add(item);
        }
        io.execute(() -> dao.replaceExchanges(result));
    }

    public void rejectReport(ReportCacheEntity.AdminReportCacheEntity report, ActionCallback callback) {
        firestore.collection("reports").document(report.reportId)
                .update("status", "REJECTED", "updatedAt", FieldValue.serverTimestamp())
                .addOnSuccessListener(unused -> callback.onSuccess("Đã từ chối báo cáo."))
                .addOnFailureListener(e -> callback.onError("Không thể cập nhật report: " + e.getMessage()));
    }

    public void confirmReportViolation(ReportCacheEntity.AdminReportCacheEntity report, ActionCallback callback) {
        boolean isAccount = "ACCOUNT".equalsIgnoreCase(report.targetType)
                || "USER".equalsIgnoreCase(report.targetType);

        if (report.targetId == null || report.targetId.trim().isEmpty()) {
            callback.onError(
                    "Report chưa có mã đối tượng hợp lệ. Hãy kiểm tra target_accountId/target_postId."
            );
            return;
        }

        if (isAccount) {
            String targetId = report.targetId;
            WriteBatch batch = firestore.batch();
            batch.update(firestore.collection("accounts").document(targetId), "status", "LOCKED");
            batch.update(firestore.collection("reports").document(report.reportId),
                    "status", "RESOLVED", "updatedAt", FieldValue.serverTimestamp());
            addNotification(batch, targetId, "ACCOUNT_LOCKED", "Tài khoản bạn đã bị khóa",
                    "Tài khoản của bạn đã bị khóa do vi phạm quy định của StayExchange.");

            batch.commit()
                    .addOnSuccessListener(unused ->
                            callback.onSuccess("Đã khóa tài khoản và tạo thông báo cho User."))
                    .addOnFailureListener(e ->
                            callback.onError("Không thể xử lý report: " + e.getMessage()));
            return;
        }

        if (!"POST".equalsIgnoreCase(report.targetType)) {
            callback.onError("Loại report không hợp lệ: " + report.targetType);
            return;
        }

        findPostForReport(report.targetId, new PostLookupCallback() {
            @Override
            public void onFound(DocumentSnapshot post) {
                String actualPostId = post.getId();
                String ownerId = firstString(
                        post, "accountId", "ownerId", "userId", "owner_accountId"
                );

                if (ownerId.isEmpty()) {
                    callback.onError(
                            "Đã tìm thấy bài đăng nhưng không có trường accountId/ownerId để gửi thông báo."
                    );
                    return;
                }

                WriteBatch batch = firestore.batch();
                batch.update(
                        firestore.collection("posts").document(actualPostId),
                        "status", "HIDDEN"
                );
                batch.update(
                        firestore.collection("reports").document(report.reportId),
                        "status", "RESOLVED",
                        "updatedAt", FieldValue.serverTimestamp()
                );
                addNotification(
                        batch,
                        ownerId,
                        "POST_HIDDEN",
                        "Bài đăng đã bị ẩn",
                        "Bài đăng của bạn đã bị ẩn do vi phạm quy định của StayExchange.",
                        actualPostId
                );

                batch.commit()
                        .addOnSuccessListener(unused ->
                                callback.onSuccess(
                                        "Đã ẩn bài đăng và tạo thông báo cho chủ bài đăng."
                                ))
                        .addOnFailureListener(e ->
                                callback.onError("Không thể xử lý report: " + e.getMessage()));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void decideCancellation(ExchangeCacheEntity exchange, boolean accept, ActionCallback callback) {
        if(exchange.walletManaged){callback.onError("Giao dịch ví phải được hủy và hoàn tiền trong chi tiết thanh toán.");return;}
        try {
            org.json.JSONObject body=new org.json.JSONObject();body.put("exchangeId",exchange.exchangeId);body.put("cancelDecision",accept);
            com.example.mobileproject.wallet.WalletApi.call(application,"/v1/exchanges/update",body,(data,error)->{
                if(error!=null)callback.onError(error.getMessage());
                else callback.onSuccess(accept?"Đã chấp nhận yêu cầu hủy.":"Đã từ chối yêu cầu hủy.");
            });
        }catch(Exception error){callback.onError(error.getMessage());}
    }

    private void addNotification(WriteBatch batch, String accountId, String type, String title, String body) {
        addNotification(batch, accountId, type, title, body, "");
    }
    private void addNotification(WriteBatch batch, String accountId, String type, String title, String body, String postId) {
        com.google.firebase.firestore.DocumentReference ref = firestore.collection("notifications").document();
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", ref.getId());
        if (!postId.isEmpty()) data.put("postId", postId);
        data.put("accountId", accountId);
        String recipientUid=accountId;
        for(DocumentSnapshot account:latestAccounts){
            if(accountId.equals(account.getId())||accountId.equals(firstString(account,"accountId"))){
                String mapped=firstString(account,"authUid","googleUid");recipientUid=mapped.isEmpty()?account.getId():mapped;break;
            }
        }
        data.put("userId",recipientUid);data.put("recipientId",recipientUid);
        data.put("title", title);
        data.put("body", body);
        data.put("type", type);
        data.put("isRead", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(ref, data);
    }

    private static boolean hasAny(DocumentSnapshot doc, String... keys) {
        for (String key : keys) if (doc.get(key) != null) return true;
        return false;
    }

    private static String firstString(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            Object value = doc.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) return String.valueOf(value);
        }
        return "";
    }

    private static long dateMillis(Object value) {
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }

    private static int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return 0.0;
    }
}
