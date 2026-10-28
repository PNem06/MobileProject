package com.example.mobileproject.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mobileproject.data.AdminUserCache;
import com.example.mobileproject.model.AdminUserItem;
import com.example.mobileproject.model.AdminUserPostItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdminUserRepository {

    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private final AdminUserCache cache;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final List<AdminUserItem> users = new ArrayList<>();
    private final List<DocumentSnapshot> accountRows = new ArrayList<>();
    private final Map<String, Integer> postCounts = new HashMap<>();
    private final List<ListenerRegistration> registrations = new ArrayList<>();

    private final List<AdminUserPostItem> postItems = new ArrayList<>();
    private final List<AdminUserPostItem> rawPostItems = new ArrayList<>();

    private final Map<String, String> currentImages = new HashMap<>();
    private final Map<String, String> legacyImages = new HashMap<>();

    private boolean postDetailsLoaded;
    private boolean postDetailsEnabled;

    private String uid;
    private String notice = "Đang kiểm tra quyền quản trị…";

    private boolean authorized;
    private boolean denied;
    private boolean connected;
    private boolean busy;
    private boolean loaded;
    private boolean listening;
    private boolean dataStarted;
    private boolean ownServer;
    private boolean accountsServer;
    private boolean postsServer;
    private boolean postsLoaded;
    private boolean accountsLoaded;
    private boolean closed;

    private int generation;

    private final FirebaseAuth.AuthStateListener sessionListener =
            firebaseAuth -> {
                if (listening
                        && (firebaseAuth.getCurrentUser() == null
                        || !firebaseAuth.getCurrentUser()
                        .getUid().equals(uid))) {
                    deny("Phiên đăng nhập đã thay đổi.");
                }
            };

    public AdminUserRepository(Context context) {
        cache = new AdminUserCache(context.getApplicationContext());
        refreshUi();
    }

    public void enablePostDetails() {
        if (postDetailsEnabled) {
            return;
        }

        postDetailsEnabled = true;

        if (listening) {
            stop();
            start();
        }
    }

    public LiveData<State> getState() {
        return state;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public void consumeMessage() {
        message.setValue(null);
    }

    public boolean writable() {
        return authorized
                && connected
                && ownServer
                && accountsServer
                && !busy;
    }

    public void refreshUi() {
        state.setValue(new State(
                users,
                authorized,
                denied,
                busy,
                loaded,
                writable(),
                notice,
                sourceLabel(),
                postItems,
                postDetailsLoaded
        ));
    }

    public static final class State {

        public final List<AdminUserItem> users;
        public final boolean authorized;
        public final boolean denied;
        public final boolean busy;
        public final boolean loaded;
        public final boolean writable;
        public final String notice;
        public final String source;
        public final List<AdminUserPostItem> posts;
        public final boolean postDetailsLoaded;

        private State(
                List<AdminUserItem> users,
                boolean authorized,
                boolean denied,
                boolean busy,
                boolean loaded,
                boolean writable,
                String notice,
                String source,
                List<AdminUserPostItem> posts,
                boolean postDetailsLoaded
        ) {
            this.users = Collections.unmodifiableList(
                    new ArrayList<>(users)
            );

            this.authorized = authorized;
            this.denied = denied;
            this.busy = busy;
            this.loaded = loaded;
            this.writable = writable;
            this.notice = notice;
            this.source = source;

            this.posts = Collections.unmodifiableList(
                    new ArrayList<>(posts)
            );

            this.postDetailsLoaded = postDetailsLoaded;
        }
    }

    public void setConnected(boolean value) {
        connected = value;
        refreshUi();
    }

    public void start() {
        if (closed || listening) {
            return;
        }

        if (auth.getCurrentUser() == null) {
            deny("Bạn chưa đăng nhập.");
            return;
        }

        String currentUid = auth.getCurrentUser().getUid();

        if (uid != null && !uid.equals(currentUid)) {
            users.clear();
            accountRows.clear();
            postCounts.clear();
            postItems.clear();
            rawPostItems.clear();
            currentImages.clear();
            legacyImages.clear();

            postDetailsLoaded = false;
            loaded = false;
            authorized = false;
            busy = false;
            postsLoaded = false;
        }

        uid = currentUid;
        listening = true;
        denied = false;
        dataStarted = false;
        accountsLoaded = false;

        ownServer = false;
        accountsServer = false;
        postsServer = false;

        final int token = ++generation;

        auth.addAuthStateListener(sessionListener);

        registrations.add(
                db.collection("accounts")
                        .document(uid)
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (doc, error) -> {
                                    if (!valid(token)) {
                                        return;
                                    }

                                    if (error != null) {
                                        fail(error);
                                        return;
                                    }

                                    if (doc == null) {
                                        return;
                                    }

                                    if (!doc.exists()
                                            && doc.getMetadata().isFromCache()) {
                                        notice = "Cần kết nối mạng để xác minh "
                                                + "quyền quản trị lần đầu.";
                                        refreshUi();
                                        return;
                                    }

                                    if (!"ADMIN".equals(doc.getString("role"))
                                            || !"ACTIVE".equals(
                                            doc.getString("status"))) {
                                        deny("Chỉ tài khoản quản trị đang hoạt động "
                                                + "mới được truy cập.");
                                        return;
                                    }

                                    authorized = true;
                                    notice = "";

                                    ownServer = !doc.getMetadata().isFromCache()
                                            && !doc.getMetadata().hasPendingWrites();

                                    if (!dataStarted) {
                                        dataStarted = true;

                                        disk.execute(() -> {
                                            List<AdminUserItem> saved;
                                            List<AdminUserPostItem> savedPosts;

                                            try {
                                                savedPosts = postDetailsEnabled
                                                        ? cache.readPosts(currentUid)
                                                        : null;
                                            } catch (Exception e) {
                                                savedPosts = null;
                                            }

                                            final List<AdminUserPostItem> restoredPosts =
                                                    savedPosts;

                                            try {
                                                saved = cache.read(currentUid);
                                            } catch (Exception e) {
                                                saved = null;
                                            }

                                            final List<AdminUserItem> restored = saved;

                                            main.post(() -> {
                                                if (!valid(token) || !authorized) {
                                                    return;
                                                }

                                                if (!loaded && restored != null) {
                                                    users.clear();
                                                    users.addAll(restored);
                                                    loaded = true;
                                                    refreshUi();
                                                }

                                                if (!postDetailsLoaded
                                                        && restoredPosts != null) {
                                                    postItems.clear();
                                                    postItems.addAll(restoredPosts);
                                                    postDetailsLoaded = true;
                                                    refreshUi();
                                                }

                                                listenData(token);
                                            });
                                        });
                                    }

                                    if (accountsLoaded) {
                                        rebuild();
                                    } else {
                                        refreshUi();
                                    }
                                }
                        )
        );
    }

    private void listenData(int token) {
        registrations.add(
                db.collection("accounts")
                        .whereEqualTo("role", "USER")
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (snapshot, error) -> {
                                    if (!valid(token)) {
                                        return;
                                    }

                                    if (error != null) {
                                        fail(error);
                                        return;
                                    }

                                    if (snapshot == null) {
                                        return;
                                    }

                                    accountsServer =
                                            !snapshot.getMetadata().isFromCache()
                                                    && !snapshot.getMetadata()
                                                    .hasPendingWrites();

                                    if (!accountsServer
                                            && (loaded || snapshot.isEmpty())) {
                                        refreshUi();
                                        return;
                                    }

                                    accountRows.clear();
                                    accountRows.addAll(snapshot.getDocuments());
                                    accountsLoaded = true;

                                    rebuild();
                                }
                        )
        );

        registrations.add(
                db.collection("posts")
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (snapshot, error) -> {
                                    if (!valid(token)) {
                                        return;
                                    }

                                    if (error != null) {
                                        fail(error);
                                        return;
                                    }

                                    if (snapshot == null) {
                                        return;
                                    }

                                    postsServer =
                                            !snapshot.getMetadata().isFromCache()
                                                    && !snapshot.getMetadata()
                                                    .hasPendingWrites();

                                    if (postsServer) {
                                        postCounts.clear();
                                        rawPostItems.clear();

                                        for (DocumentSnapshot doc
                                                : snapshot.getDocuments()) {
                                            if (postDetailsEnabled) {
                                                rawPostItems.add(
                                                        new AdminUserPostItem(
                                                                doc.getId(),
                                                                doc.getData()
                                                        )
                                                );
                                            }

                                            String owner = AdminUserItem.first(
                                                    doc.getData(),
                                                    "accountId",
                                                    AdminUserItem.first(
                                                            doc.getData(),
                                                            "ownerId",
                                                            ""
                                                    )
                                            );

                                            if (!owner.isEmpty()) {
                                                postCounts.put(
                                                        owner,
                                                        postCounts.getOrDefault(
                                                                owner,
                                                                0
                                                        ) + 1
                                                );
                                            }
                                        }

                                        postsLoaded = true;

                                        if (postDetailsEnabled) {
                                            postDetailsLoaded = true;
                                            rebuildPostImages();
                                        }

                                        if (accountsLoaded) {
                                            rebuild();
                                        }
                                    }

                                    refreshUi();
                                }
                        )
        );

        if (postDetailsEnabled) {
            listenImages("images", currentImages, token);
            listenImages("postImages", legacyImages, token);
        }
    }

    private void listenImages(
            String collection,
            Map<String, String> target,
            int token
    ) {
        registrations.add(
                db.collection(collection)
                        .addSnapshotListener(
                                MetadataChanges.INCLUDE,
                                (snapshot, error) -> {
                                    if (!valid(token)
                                            || error != null
                                            || snapshot == null
                                            || snapshot.getMetadata()
                                            .hasPendingWrites()) {
                                        return;
                                    }

                                    target.clear();

                                    List<DocumentSnapshot> rows =
                                            new ArrayList<>(
                                                    snapshot.getDocuments()
                                            );

                                    rows.sort(
                                            Comparator.comparingDouble(
                                                    (DocumentSnapshot doc) -> {
                                                        Object order =
                                                                doc.get("orderIndex");

                                                        return order instanceof Number
                                                                ? ((Number) order)
                                                                .doubleValue()
                                                                : 0;
                                                    }
                                            ).thenComparing(
                                                    DocumentSnapshot::getId
                                            )
                                    );

                                    for (DocumentSnapshot doc : rows) {
                                        String id = AdminUserItem.first(
                                                doc.getData(),
                                                "postId",
                                                ""
                                        );

                                        String url = AdminUserItem.first(
                                                doc.getData(),
                                                "imgUrl",
                                                AdminUserItem.first(
                                                        doc.getData(),
                                                        "imageUrl",
                                                        ""
                                                )
                                        );

                                        if (!id.isEmpty() && !url.isEmpty()) {
                                            target.putIfAbsent(id, url);
                                        }
                                    }

                                    if (postsLoaded) {
                                        rebuildPostImages();
                                        savePostDetails();
                                        refreshUi();
                                    }
                                }
                        )
        );
    }

    private void rebuildPostImages() {
        postItems.clear();

        for (AdminUserPostItem post : rawPostItems) {
            String url = currentImages.get(post.documentId);

            if (url == null) {
                url = currentImages.get(post.postId);
            }

            if (url == null) {
                url = legacyImages.get(post.documentId);
            }

            if (url == null) {
                url = legacyImages.get(post.postId);
            }

            postItems.add(post.withImage(url));
        }

        postItems.sort(
                Comparator.comparing(
                        (AdminUserPostItem p) ->
                                AdminUserItem.normalize(p.title)
                ).thenComparing(p -> p.documentId)
        );
    }

    private void savePostDetails() {
        if (!postDetailsEnabled
                || !ownServer
                || !accountsServer
                || !postsServer) {
            return;
        }

        List<AdminUserPostItem> saved = new ArrayList<>(postItems);
        String scope = uid;

        disk.execute(() -> {
            try {
                cache.writePosts(scope, saved);
            } catch (Exception e) {
                main.post(() -> {
                    if (!closed) {
                        message.setValue(
                                "Chưa lưu được danh sách bài đăng "
                                        + "để xem khi mất mạng."
                        );
                    }
                });
            }
        });
    }

    private void rebuild() {
        Map<String, AdminUserItem> old = new HashMap<>();

        for (AdminUserItem user : users) {
            old.put(user.documentId, user);
        }

        users.clear();

        for (DocumentSnapshot doc : accountRows) {
            String accountId = AdminUserItem.first(
                    doc.getData(),
                    "accountId",
                    doc.getId()
            );

            int count;

            if (postsLoaded) {
                count = postCounts.getOrDefault(doc.getId(), 0);

                if (!accountId.equals(doc.getId())) {
                    count += postCounts.getOrDefault(accountId, 0);
                }
            } else {
                AdminUserItem previous = old.get(doc.getId());
                count = previous == null ? -1 : previous.postCount;
            }

            users.add(new AdminUserItem(
                    doc.getId(),
                    doc.getData(),
                    count
            ));
        }

        users.sort(
                Comparator.comparing(
                        user -> AdminUserItem.normalize(user.fullName)
                )
        );

        loaded = true;
        notice = "";

        savePostDetails();

        if (ownServer && accountsServer && postsServer) {
            List<AdminUserItem> snapshot = new ArrayList<>(users);
            String scope = uid;

            disk.execute(() -> {
                try {
                    cache.write(scope, snapshot);
                } catch (Exception e) {
                    main.post(() -> message.setValue(
                            "Không lưu được dữ liệu để xem khi mất mạng."
                    ));
                }
            });
        }

        refreshUi();
    }

    public void changeStatus(AdminUserItem target) {
        if (!writable()) {
            message.setValue(
                    "Cần kết nối mạng và xác minh quyền để đổi trạng thái."
            );
            return;
        }

        if (!target.canChangeStatus()) {
            message.setValue("Trạng thái hiện tại không hợp lệ.");
            return;
        }

        busy = true;
        refreshUi();

        final String actor = uid;
        final String next = target.active() ? "INACTIVE" : "ACTIVE";

        db.runTransaction(transaction -> {
            DocumentSnapshot admin = transaction.get(
                    db.collection("accounts").document(actor)
            );

            DocumentReference ref = db.collection("accounts")
                    .document(target.documentId);

            DocumentSnapshot account = transaction.get(ref);

            if (auth.getCurrentUser() == null
                    || !actor.equals(auth.getCurrentUser().getUid())
                    || !"ADMIN".equals(admin.getString("role"))
                    || !"ACTIVE".equals(admin.getString("status"))) {
                throw new FirebaseFirestoreException(
                        "Quyền admin đã thay đổi.",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED
                );
            }

            if (!account.exists()
                    || actor.equals(target.documentId)
                    || !"USER".equals(account.getString("role"))) {
                throw new FirebaseFirestoreException(
                        "Chỉ được đổi trạng thái tài khoản USER.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION
                );
            }

            if (!target.status.equals(account.getString("status"))) {
                throw new FirebaseFirestoreException(
                        "Trạng thái đã được admin khác cập nhật. "
                                + "Hãy kiểm tra lại.",
                        FirebaseFirestoreException.Code.ABORTED
                );
            }

            transaction.update(ref, "status", next);
            return null;

        }).addOnSuccessListener(unused -> {
            if (closed || !actor.equals(uid)) {
                return;
            }

            busy = false;

            message.setValue(
                    "Đã "
                            + ("ACTIVE".equals(next) ? "mở khóa" : "khóa")
                            + " tài khoản."
            );

            refreshUi();

        }).addOnFailureListener(error -> {
            if (closed || !actor.equals(uid)) {
                return;
            }

            busy = false;

            message.setValue(
                    "Chưa thay đổi trạng thái: " + error.getMessage()
            );

            refreshUi();
        });
    }

    public String sourceLabel() {
        if (!authorized) {
            return notice;
        }

        if (!notice.isEmpty()) {
            return notice;
        }

        if (!loaded) {
            return connected
                    ? "Đang tải dữ liệu…"
                    : "Chưa có dữ liệu đã lưu trên máy.";
        }

        if (connected && ownServer && accountsServer && postsServer) {
            return "Đã đồng bộ · Cập nhật trực tiếp";
        }

        return "Đang xem bản lưu gần nhất · "
                + "kết nối mạng để cập nhật và đổi trạng thái";
    }

    private boolean valid(int token) {
        return listening
                && generation == token
                && auth.getCurrentUser() != null
                && uid.equals(auth.getCurrentUser().getUid());
    }

    private void fail(FirebaseFirestoreException error) {
        if (error.getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED
                || error.getCode()
                == FirebaseFirestoreException.Code.UNAUTHENTICATED) {
            deny("Bạn không có quyền đọc dữ liệu quản trị.");
        } else {
            accountsServer = false;
            postsServer = false;

            notice = "Chưa tải được dữ liệu mới. " + error.getMessage();
            refreshUi();
        }
    }

    private void deny(String reason) {
        authorized = false;
        denied = true;

        users.clear();
        accountRows.clear();
        postItems.clear();
        rawPostItems.clear();
        currentImages.clear();
        legacyImages.clear();

        postDetailsLoaded = false;
        postsLoaded = false;
        loaded = false;
        notice = reason;

        String scope = uid;

        if (scope != null && !closed) {
            disk.execute(() -> {
                try {
                    cache.clear(scope);
                } catch (RuntimeException ignored) {
                }
            });
        }

        stop();
        refreshUi();
    }

    public void stop() {
        listening = false;
        generation++;

        auth.removeAuthStateListener(sessionListener);

        for (ListenerRegistration registration : registrations) {
            registration.remove();
        }

        registrations.clear();
        dataStarted = false;

        ownServer = false;
        accountsServer = false;
        postsServer = false;

        refreshUi();
    }

    public void close() {
        if (closed) {
            return;
        }

        stop();
        closed = true;

        disk.execute(cache::close);
        disk.shutdown();
    }
}