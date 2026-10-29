package com.example.mobileproject.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.util.PostImageUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPIENT_ID = "recipientId";
    public static final String EXTRA_PARTNER_NAME = "partnerName";
    public static final String EXTRA_PARTNER_AVATAR = "partnerAvatarUrl";

    private FirebaseFirestore db;
    private String currentUid = "";
    private String partnerId = "";
    private String conversationId = "";

    private View layoutInputBar, layoutBlockedNotice;
    private TextView txtBlockedNotice, btnUnblock;
    private boolean sending, blockStatusKnown;
    private boolean iBlockedPartner = false;
    private boolean partnerBlockedMe = false;

    private ImageView btnBack, btnSendMessage;
    private ShapeableImageView imgPartnerAvatar;
    private TextView txtPartnerName;
    private EditText edtMessageInput;
    private RecyclerView recyclerMessages;

    private MessageAdapter adapter;
    private final List<ChatMessageItem> messageList = new ArrayList<>();
    private ListenerRegistration messagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid() == null ? "" : FirebaseAuth.getInstance().getUid();

        if (getIntent() != null) {
            partnerId = getIntent().getStringExtra(EXTRA_RECIPIENT_ID);
            if (partnerId == null || partnerId.isEmpty()) partnerId = getIntent().getStringExtra("partnerId");
            if (partnerId == null || partnerId.isEmpty()) partnerId = getIntent().getStringExtra("otherUserId");
        }

        if (partnerId == null) partnerId = "";

        if (currentUid.isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập để nhắn tin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (partnerId.isEmpty() || partnerId.equals(currentUid)) {
            Toast.makeText(this, "Không thể bắt đầu cuộc trò chuyện.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        conversationId = currentUid.compareTo(partnerId) < 0
                ? currentUid + "_" + partnerId
                : partnerId + "_" + currentUid;

        initViews();
        loadPartnerProfile();
        setupRecyclerView();
        checkBlockStatus();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        imgPartnerAvatar = findViewById(R.id.imgPartnerAvatar);
        txtPartnerName = findViewById(R.id.txtPartnerName);
        edtMessageInput = findViewById(R.id.edtMessageInput);
        btnSendMessage = findViewById(R.id.btnSendMessage);
        recyclerMessages = findViewById(R.id.recyclerMessages);

        layoutInputBar = findViewById(R.id.layoutInputBar);
        layoutBlockedNotice = findViewById(R.id.layoutBlockedNotice);
        txtBlockedNotice = findViewById(R.id.txtBlockedNotice);
        btnUnblock = findViewById(R.id.btnUnblock);

        btnBack.setOnClickListener(v -> finish());
        btnSendMessage.setOnClickListener(v -> sendMessage());

        if (imgPartnerAvatar != null) {
            imgPartnerAvatar.setOnLongClickListener(v -> {
                showBlockMenuDialog();
                return true;
            });
        }
        if (txtPartnerName != null) {
            txtPartnerName.setOnLongClickListener(v -> {
                showBlockMenuDialog();
                return true;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBlockStatus();
    }

    private void checkBlockStatus() {
        blockStatusKnown=false;
        if(btnSendMessage!=null)btnSendMessage.setEnabled(false);
        db.collection("blocks").whereEqualTo("blockerId",currentUid).whereEqualTo("blockedUserId",partnerId).get()
                .addOnSuccessListener(mine->{
                    if(isFinishing()||isDestroyed())return;
                    iBlockedPartner=!mine.isEmpty();
                    db.collection("blocks").whereEqualTo("blockerId",partnerId).whereEqualTo("blockedUserId",currentUid).get()
                            .addOnSuccessListener(theirs->{
                                if(isFinishing()||isDestroyed())return;
                                partnerBlockedMe=!theirs.isEmpty();
                                db.collection("accounts").document(partnerId).get()
                                        .addOnSuccessListener(account -> {
                                            if(isFinishing()||isDestroyed())return;
                                            if(account.exists()&&"LOCKED".equalsIgnoreCase(account.getString("status"))){
                                                Toast.makeText(this,"Cuộc trò chuyện này không còn khả dụng.",Toast.LENGTH_SHORT).show();
                                                finish();
                                                return;
                                            }
                                            blockStatusKnown=true;updateBlockUi();ensureConversation();
                                        })
                                        .addOnFailureListener(error->blockCheckFailed());
                            }).addOnFailureListener(error->blockCheckFailed());
                }).addOnFailureListener(error->blockCheckFailed());
    }
    private void blockCheckFailed(){
        if(isFinishing()||isDestroyed())return;
        blockStatusKnown=false;btnSendMessage.setEnabled(false);
        Toast.makeText(this,"Chưa kiểm tra được trạng thái nhắn tin. Vui lòng mở lại cuộc trò chuyện.",Toast.LENGTH_SHORT).show();
    }
    private Map<String,Object> participants(){
        Map<String,Object> value=new HashMap<>();
        value.put("participant1Id",currentUid.compareTo(partnerId)<0?currentUid:partnerId);
        value.put("participant2Id",currentUid.compareTo(partnerId)<0?partnerId:currentUid);
        return value;
    }
    private void ensureConversation(){
        db.collection("conversations").document(conversationId).set(participants(),SetOptions.merge())
                .addOnSuccessListener(unused->{if(!isFinishing()&&!isDestroyed())listenToMessages();})
                .addOnFailureListener(error->{if(!isFinishing()&&!isDestroyed())Toast.makeText(this,"Không thể mở cuộc trò chuyện.",Toast.LENGTH_SHORT).show();});
    }

    private void updateBlockUi() {
        btnSendMessage.setEnabled(blockStatusKnown&&!sending&&!iBlockedPartner&&!partnerBlockedMe);
        if (iBlockedPartner) {
            if (layoutInputBar != null) layoutInputBar.setVisibility(View.GONE);
            if (layoutBlockedNotice != null) layoutBlockedNotice.setVisibility(View.VISIBLE);
            if (txtBlockedNotice != null) txtBlockedNotice.setText("Bạn đã chặn người dùng này.");
            if (btnUnblock != null) {
                btnUnblock.setVisibility(View.VISIBLE);
                btnUnblock.setOnClickListener(v -> unblockUser());
            }
        } else if (partnerBlockedMe) {
            if (layoutInputBar != null) layoutInputBar.setVisibility(View.GONE);
            if (layoutBlockedNotice != null) layoutBlockedNotice.setVisibility(View.VISIBLE);
            if (txtBlockedNotice != null) txtBlockedNotice.setText("Bạn đã bị người dùng này chặn.");
            if (btnUnblock != null) btnUnblock.setVisibility(View.GONE);
        } else {
            if (layoutInputBar != null) layoutInputBar.setVisibility(View.VISIBLE);
            if (layoutBlockedNotice != null) layoutBlockedNotice.setVisibility(View.GONE);
        }
    }

    private void showBlockMenuDialog() {
        String name = txtPartnerName.getText().toString();
        String optionText = iBlockedPartner ? "Bỏ chặn " + name : "Chặn " + name;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{optionText}, (dialog, which) -> {
                    if (iBlockedPartner) {
                        unblockUser();
                    } else {
                        blockUser();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void blockUser() {
        if (currentUid.isEmpty() || partnerId.isEmpty()) return;
        String docId = currentUid + "_" + partnerId;

        Map<String, Object> blockData = new HashMap<>();
        blockData.put("blockId", docId);
        blockData.put("blockerId", currentUid);
        blockData.put("blockedUserId", partnerId);
        blockData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("blocks").document(docId).set(blockData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã chặn người dùng", Toast.LENGTH_SHORT).show();
                    checkBlockStatus();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Chặn thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void unblockUser() {
        if (currentUid.isEmpty() || partnerId.isEmpty()) return;
        String docId = currentUid + "_" + partnerId;

        db.collection("blocks").document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã bỏ chặn người dùng", Toast.LENGTH_SHORT).show();
                    checkBlockStatus();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Bỏ chặn thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void setupRecyclerView() {
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(manager);

        adapter = new MessageAdapter(messageList, currentUid);
        recyclerMessages.setAdapter(adapter);
    }

    private void loadPartnerProfile() {
        String passedName = getIntent().getStringExtra(EXTRA_PARTNER_NAME);
        String passedAvatar = getIntent().getStringExtra(EXTRA_PARTNER_AVATAR);

        if (passedName != null && !passedName.isEmpty()) txtPartnerName.setText(passedName);
        if (passedAvatar != null && !passedAvatar.isEmpty()) {
            Glide.with(this).load(PostImageUtils.asGlideModel(passedAvatar)).placeholder(R.drawable.ic_user).into(imgPartnerAvatar);
        }

        db.collection("accounts").document(partnerId).get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (doc.exists()) {
                        String name = doc.getString("fullName");
                        if (name == null || name.isEmpty()) name = doc.getString("username");
                        if (name != null && !name.isEmpty()) txtPartnerName.setText(name);

                        String avatar = doc.getString("avatarUrl");
                        if (avatar != null && !avatar.isEmpty()) {
                            Glide.with(this).load(PostImageUtils.asGlideModel(avatar)).placeholder(R.drawable.ic_user).into(imgPartnerAvatar);
                        }
                    }
                });
    }

    private void listenToMessages() {
        messagesListener = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (error != null || snapshot == null) return;

                    messageList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String sender = doc.getString("senderId");
                        String content = doc.getString("content");
                        Date time = null;
                        Object sentAt = doc.get("sentAt");
                        if (sentAt instanceof Timestamp) time = ((Timestamp) sentAt).toDate();
                        else if (sentAt instanceof Date) time = (Date) sentAt;
                        else time = new Date();

                        if (sender != null && content != null) {
                            messageList.add(new ChatMessageItem(sender, content, time));
                        }
                    }

                    messageList.sort((a, b) -> {
                        if (a.time == null && b.time == null) return 0;
                        if (a.time == null) return 1;
                        if (b.time == null) return -1;
                        return a.time.compareTo(b.time);
                    });

                    adapter.notifyDataSetChanged();
                    if (!messageList.isEmpty()) {
                        recyclerMessages.smoothScrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private void sendMessage() {
        final String text=edtMessageInput.getText().toString().trim();
        if(text.isEmpty()||sending||!blockStatusKnown||iBlockedPartner||partnerBlockedMe)return;
        sending=true;btnSendMessage.setEnabled(false);
        com.google.firebase.firestore.DocumentReference conversation=db.collection("conversations").document(conversationId);
        Map<String,Object> message=new HashMap<>();message.put("senderId",currentUid);message.put("content",text);message.put("sentAt",FieldValue.serverTimestamp());
        Map<String,Object> summary=participants();summary.put("lastMessage",text);summary.put("lastMessageSenderId",currentUid);summary.put("lastMessageAt",FieldValue.serverTimestamp());summary.put("isRead",false);
        com.google.firebase.firestore.WriteBatch batch=db.batch();
        batch.set(conversation,summary,SetOptions.merge());batch.set(conversation.collection("messages").document(),message);
        batch.commit().addOnSuccessListener(unused->{
            notifyMessage(text);
            if(isFinishing()||isDestroyed())return;
            sending=false;
            if(text.equals(edtMessageInput.getText().toString().trim()))edtMessageInput.setText("");
            updateBlockUi();
        }).addOnFailureListener(error->{
            if(isFinishing()||isDestroyed())return;
            sending=false;checkBlockStatus();
            Toast.makeText(this,"Chưa gửi được tin nhắn. Nội dung được giữ lại để thử lại.",Toast.LENGTH_SHORT).show();
        });
    }
    private void notifyMessage(String text){
        Map<String,Object> notice=new HashMap<>();notice.put("userId",partnerId);notice.put("senderId",currentUid);notice.put("type","chat_message");
        notice.put("conversationId",conversationId);notice.put("title","Bạn có tin nhắn mới");notice.put("body",text);notice.put("isRead",false);notice.put("createdAt",FieldValue.serverTimestamp());
        db.collection("notifications").add(notice);
    }

    @Override
    protected void onDestroy() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        super.onDestroy();
    }

    private static class ChatMessageItem {
        final String senderId;
        final String content;
        final Date time;

        ChatMessageItem(String senderId, String content, Date time) {
            this.senderId = senderId;
            this.content = content;
            this.time = time;
        }
    }

    private static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_SENT = 1;
        private static final int TYPE_RECEIVED = 2;

        private final List<ChatMessageItem> items;
        private final String currentUid;
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));

        MessageAdapter(List<ChatMessageItem> items, String currentUid) {
            this.items = items;
            this.currentUid = currentUid;
        }

        @Override
        public int getItemViewType(int position) {
            ChatMessageItem item = items.get(position);
            return currentUid.equals(item.senderId) ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_SENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_sent, parent, false);
                return new SentHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_received, parent, false);
                return new ReceivedHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessageItem item = items.get(position);
            String formattedTime = item.time != null ? timeFormat.format(item.time) : "";

            if (holder instanceof SentHolder) {
                ((SentHolder) holder).txtContent.setText(item.content);
                ((SentHolder) holder).txtTime.setText(formattedTime);
            } else if (holder instanceof ReceivedHolder) {
                ((ReceivedHolder) holder).txtContent.setText(item.content);
                ((ReceivedHolder) holder).txtTime.setText(formattedTime);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class SentHolder extends RecyclerView.ViewHolder {
            TextView txtContent, txtTime;
            SentHolder(@NonNull View itemView) {
                super(itemView);
                txtContent = itemView.findViewById(R.id.txtMessageContent);
                txtTime = itemView.findViewById(R.id.txtMessageTime);
            }
        }

        static class ReceivedHolder extends RecyclerView.ViewHolder {
            TextView txtContent, txtTime;
            ReceivedHolder(@NonNull View itemView) {
                super(itemView);
                txtContent = itemView.findViewById(R.id.txtMessageContent);
                txtTime = itemView.findViewById(R.id.txtMessageTime);
            }
        }
    }
}
