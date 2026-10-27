
package com.example.mobileproject.repository;

import com.example.mobileproject.model.Post;
import com.example.mobileproject.data.HomePostMapper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PostRepository {

    public interface OnPostsLoadedListener {
        void onSuccess(List<Post> posts);
        void onError(Exception e);
    }

    private final FirebaseFirestore db =
            FirebaseFirestore.getInstance();
    public com.google.firebase.firestore.ListenerRegistration listenToApprovedPosts(OnPostsLoadedListener listener) {
        return db.collection("posts")
                .whereIn("status", java.util.Arrays.asList(Post.APPROVED, "BOOKED"))
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        listener.onError(e);
                        return;
                    }
                    if (snapshot == null) return;
                    List<Post> posts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (isArchivedRepost(doc)) continue;
                        try {
                            Post post = mapDocumentToPost(doc);
                            posts.add(post);
                        } catch (Exception ex) {
                            android.util.Log.e("PostRepository", "Không thể đọc bài đăng: " + doc.getId(), ex);
                        }
                    }
                    listener.onSuccess(posts);
                });
    }

    public void getApprovedPosts(OnPostsLoadedListener listener) {
        db.collection("posts")
                .whereIn("status", java.util.Arrays.asList(Post.APPROVED, "BOOKED"))
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Post> posts = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String status = doc.getString("status");

                        // Chỉ hiển thị bài đã được duyệt
                        if (!Post.APPROVED.equals(status) && !"BOOKED".equals(status)) {
                            continue;
                        }
                        if (isArchivedRepost(doc)) continue;
                        try {
                            Post post = mapDocumentToPost(doc);
                            posts.add(post);
                        } catch (Exception e) {
                            android.util.Log.e(
                                    "PostRepository",
                                    "Không thể đọc bài đăng: " + doc.getId(),
                                    e
                            );
                        }
                    }

                    listener.onSuccess(posts);
                })
                .addOnFailureListener(listener::onError);
    }

    private Post mapDocumentToPost(DocumentSnapshot doc) {
        Post post = new Post();

        post.setPostId(doc.getString("postId") != null
                ? doc.getString("postId")
                : doc.getId());

        // Firestore dùng accountId, model dùng ownerId
        post.setOwnerId(doc.getString("accountId"));

        post.setTitle(doc.getString("title"));
        post.setDescription(doc.getString("description"));

        // Firestore dùng address, model dùng addressText
        post.setAddressText(doc.getString("address"));
        post.setDestination(doc.getString("destination"));

        Double latitude = doc.getDouble("latitude");
        Double longitude = doc.getDouble("longitude");

        if (latitude != null) {
            post.setLatitude(latitude);
        }

        if (longitude != null) {
            post.setLongitude(longitude);
        }

        Long maxGuests = doc.getLong("maxGuests");
        Long bedroomCount = doc.getLong("bedroomCount");
        Long bathroomCount = doc.getLong("bathroomCount");
        Long pointsPerNight = doc.getLong("pointsPerNight");

        if (maxGuests != null && maxGuests > 0) {
            post.setMaxGuests(maxGuests.intValue());
        }

        if (bedroomCount != null && bedroomCount >= 0) {
            post.setBedroomCount(bedroomCount.intValue());
        }

        if (bathroomCount != null && bathroomCount >= 0) {
            post.setBathroomCount(bathroomCount.intValue());
        }

        if (pointsPerNight != null && pointsPerNight >= 0) {
            post.setPointsPerNight(pointsPerNight.intValue());
        }

        String status = doc.getString("status");
        if (status != null) {
            post.setStatus(status);
        }

        String exchangeType = doc.getString("exchangeType");
        if (exchangeType != null) {
            post.setExchangeType(exchangeType);
        }

        // Chuyển ngày Firestore thành Date
        Date startDate = getDate(doc, "startDate");
        Date endDate = getDate(doc, "endDate");

        // Đặt riêng để không lỗi nếu thiếu một ngày
        post.setAvailableFrom(startDate);
        post.setAvailableTo(endDate);

        // Danh sách mã tiện nghi
        List<String> amenityIds =
                (List<String>) doc.get("amenityIds");

        if (amenityIds != null) {
            post.setAmenityIds(amenityIds);
        }

        return post;
    }

    private boolean isArchivedRepost(DocumentSnapshot doc) {
        org.json.JSONObject mapped = HomePostMapper.fromDocument(doc.getId(), doc.getData());
        return HomePostMapper.isArchivedRepost(mapped);
    }

    private Date getDate(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);

        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }

        if (value instanceof Date) {
            return (Date) value;
        }

        return null;
    }
}
