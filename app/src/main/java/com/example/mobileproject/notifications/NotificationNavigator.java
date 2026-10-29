package com.example.mobileproject.notifications;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.example.mobileproject.model.RequestItem;
import com.example.mobileproject.repository.ExchangeRepository;
import com.example.mobileproject.view.ChatDetailActivity;
import com.example.mobileproject.view.Detail;
import com.example.mobileproject.view.ReceivedRequestsDetailActivity;
import com.example.mobileproject.view.SentRequestDetailActivity;
import com.example.mobileproject.view.WalletActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** Shared by the bell inbox and the foreground banner. */
public final class NotificationNavigator {
    private NotificationNavigator() { }
    public static void open(Activity activity, Notice notice) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || !active(activity,uid)) return;
        String recipient = notice.text("userId");
        if (!recipient.isEmpty() && !recipient.equals(uid)) return;
        NotificationTarget target = NotificationTarget.resolve(notice);
        switch (target.kind) {
            case POST:
                launch(activity,uid,notice,new Intent(activity,Detail.class).putExtra(Detail.EXTRA_POST_ID,target.id));
                break;
            case WALLET:
                Intent wallet = new Intent(activity,WalletActivity.class);
                if (!target.id.isEmpty()) wallet.putExtra("withdrawals".equals(target.collection)
                        ? WalletActivity.EXTRA_WITHDRAWAL_ID : WalletActivity.EXTRA_BOOKING_ID,target.id);
                launch(activity,uid,notice,wallet);
                break;
            case CHAT:
                if (!target.id.isEmpty() && !target.id.equals(uid)) {
                    launch(activity,uid,notice,chatIntent(activity,target.id));
                } else if (!target.collection.isEmpty()) {
                    FirebaseFirestore.getInstance().collection("conversations").document(target.collection).get()
                            .addOnSuccessListener(doc -> {
                                if (!active(activity,uid)) return;
                                String first = text(doc.getData(),"participant1Id"), second = text(doc.getData(),"participant2Id");
                                String partner = uid.equals(first) ? second : uid.equals(second) ? first : "";
                                if (partner.isEmpty()) unavailable(activity);
                                else launch(activity,uid,notice,chatIntent(activity,partner));
                            }).addOnFailureListener(error -> { if (active(activity,uid)) unavailable(activity); });
                } else information(activity,uid,notice);
                break;
            case REQUEST:
                new ExchangeRepository().resolveCurrentAccountId((accountId,error) -> {
                    if (active(activity,uid)) loadRequest(activity,uid,accountId == null ? uid : accountId,notice,target.id,target.collection,true);
                });
                break;
            default: information(activity,uid,notice);
        }
    }
    private static Intent chatIntent(Context context,String partner) {
        return new Intent(context,ChatDetailActivity.class).putExtra(ChatDetailActivity.EXTRA_RECIPIENT_ID,partner);
    }
    private static void loadRequest(Activity activity,String uid,String accountId,Notice notice,String id,String source,boolean fallback) {
        FirebaseFirestore.getInstance().collection(source).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!active(activity,uid)) return;
                    if (!doc.exists()) { retryOrFail(activity,uid,accountId,notice,id,source,fallback); return; }
                    Intent intent = requestIntent(activity,id,source,doc.getData(),uid,accountId);
                    if (intent == null) unavailable(activity); else launch(activity,uid,notice,intent);
                }).addOnFailureListener(error -> {
                    if (active(activity,uid)) retryOrFail(activity,uid,accountId,notice,id,source,fallback);
                });
    }
    private static void retryOrFail(Activity a,String uid,String account,Notice n,String id,String source,boolean fallback) {
        if (fallback) loadRequest(a,uid,account,n,id,source.equals("exchanges") ? "exchangeRequests" : "exchanges",false);
        else unavailable(a);
    }
    public static Intent requestIntent(Context context,String id,String source,Map<String,Object> data,String uid,String accountId) {
        Notice request = new Notice(id,data);
        if (request.flag("walletManaged")) return new Intent(context,WalletActivity.class).putExtra(WalletActivity.EXTRA_BOOKING_ID,id);
        boolean receiver = false;
        for (String key : new String[]{"hostUid","host_accountId","hostId","recipientId"}) {
            String value = request.text(key);
            receiver |= !value.isEmpty() && (uid.equals(value) || accountId.equals(value));
        }
        if (receiver) {
            String post = request.text("postId","targetPostId");
            if (post.isEmpty()) return null;
            RequestItem item = new RequestItem(); item.postId = post;
            item.postTitle = request.text("postTitle").isEmpty() ? "Yêu cầu đã nhận" : request.text("postTitle");
            item.hostId = accountId; item.isSentTab = false;
            return new Intent(context,ReceivedRequestsDetailActivity.class)
                    .putExtra(ReceivedRequestsDetailActivity.EXTRA_REQUEST_ITEM,item)
                    .putExtra(ReceivedRequestsDetailActivity.EXTRA_FOCUS_REQUEST_ID,id)
                    .putExtra(ReceivedRequestsDetailActivity.EXTRA_FOCUS_COLLECTION,source);
        }
        return new Intent(context,SentRequestDetailActivity.class)
                .putExtra(SentRequestDetailActivity.EXTRA_EXCHANGE_ID,id)
                .putExtra(SentRequestDetailActivity.EXTRA_SOURCE_COLLECTION,source);
    }
    private static String text(Map<String,Object> data,String key) { Object value = data == null ? null : data.get(key); return value instanceof String ? (String)value : ""; }
    private static boolean active(Activity activity,String uid) {
        return !activity.isFinishing() && !activity.isDestroyed() && uid.equals(FirebaseAuth.getInstance().getUid());
    }
    private static void launch(Activity activity,String uid,Notice notice,Intent intent) {
        if (!active(activity,uid)) return;
        try { activity.startActivity(intent); markRead(activity,uid,notice); }
        catch (RuntimeException error) { unavailable(activity); }
    }
    private static void information(Activity activity,String uid,Notice notice) {
        if (!active(activity,uid)) return;
        new AlertDialog.Builder(activity).setTitle(notice.title()).setMessage(notice.body())
                .setPositiveButton("Đóng",null).show();
        markRead(activity,uid,notice);
    }
    private static void markRead(Activity activity,String uid,Notice notice) {
        if (notice.id.isEmpty() || notice.read() || !active(activity,uid)) return;
        Map<String,Object> values = new HashMap<>(); values.put("isRead",true); values.put("readAt",FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("notifications").document(notice.id).update(values)
                .addOnFailureListener(error -> { if (active(activity,uid)) Toast.makeText(activity,"Chưa cập nhật được trạng thái đã đọc. Vui lòng thử lại khi có mạng.",Toast.LENGTH_SHORT).show(); });
    }
    private static void unavailable(Activity activity) {
        Toast.makeText(activity,"Không mở được chi tiết. Nội dung có thể đã bị xóa, không còn quyền xem hoặc chưa có mạng.",Toast.LENGTH_LONG).show();
    }
}
