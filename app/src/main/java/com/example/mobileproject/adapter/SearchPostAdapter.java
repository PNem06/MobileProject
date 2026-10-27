
package com.example.mobileproject.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mobileproject.R;
import com.example.mobileproject.model.ListingPost;
import com.example.mobileproject.util.PostImageUtils;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SearchPostAdapter
        extends RecyclerView.Adapter<SearchPostAdapter.PostViewHolder> {

    private final Context context;
    private final List<ListingPost> postList;
    private final OnItemClickListener listener;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Lưu tên tiện nghi đã đọc từ Firebase.
    private final Map<String, String> amenityNameCache = new HashMap<>();

    // Tránh truy vấn cùng một tiện nghi nhiều lần đồng thời.
    private final Set<String> loadingAmenityIds = new HashSet<>();

    public interface OnItemClickListener {
        void onItemClick(ListingPost post);
    }

    public SearchPostAdapter(
            Context context,
            List<ListingPost> postList,
            OnItemClickListener listener
    ) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_post, parent, false);

        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PostViewHolder holder,
            int position
    ) {
        ListingPost post = postList.get(position);

        holder.txtPostTitle.setText(
                post.getTitle() != null && !post.getTitle().trim().isEmpty()
                        ? post.getTitle()
                        : "Chỗ ở chưa có tên"
        );

        String bedBathText = post.getBedroomCount()
                + " phòng ngủ | "
                + post.getBathroomCount()
                + " phòng tắm";

        holder.txtBedBathSummary.setText(bedBathText);

        // Hiển thị tên tiện nghi thay vì amenity IDs.
        bindAmenities(holder, post);

        // Hiển thị giá theo định dạng Việt Nam.
        double price = post.getPricePerNight();

        if (price > 0) {
            NumberFormat formatter =
                    NumberFormat.getNumberInstance(new Locale("vi", "VN"));

            formatter.setMaximumFractionDigits(0);
            formatter.setMinimumFractionDigits(0);

            holder.txtCurrentPrice.setText(
                    formatter.format(price) + " đ"
            );
        } else {
            holder.txtCurrentPrice.setText("Giá chưa cập nhật");
        }

        // Hiển thị ảnh bìa.
        String imageUrl = post.getCoverImageUrl();

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            Object imageModel = PostImageUtils.asGlideModel(imageUrl);

            if (imageModel != null) {
                Glide.with(holder.itemView)
                        .load(imageModel)
                        .placeholder(R.drawable.logo_stageexchange)
                        .error(R.drawable.logo_stageexchange)
                        .into(holder.imgPostCover);
            } else {
                holder.imgPostCover.setImageResource(
                        R.drawable.logo_stageexchange
                );
            }
        } else {
            holder.imgPostCover.setImageResource(
                    R.drawable.logo_stageexchange
            );
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(post);
            }
        });
    }

    private void bindAmenities(
            @NonNull PostViewHolder holder,
            @NonNull ListingPost post
    ) {
        List<String> amenityIds = post.getAmenityIds();

        if (amenityIds == null || amenityIds.isEmpty()) {
            holder.txtAmenitiesSummary.setText("Không có tiện nghi");
            return;
        }

        // Chỉ giữ các ID hợp lệ.
        List<String> validIds = new ArrayList<>();

        for (String id : amenityIds) {
            if (id != null && !id.trim().isEmpty()) {
                validIds.add(id.trim());
            }
        }

        if (validIds.isEmpty()) {
            holder.txtAmenitiesSummary.setText("Không có tiện nghi");
            return;
        }

        // Tránh để card hiển thị tên của bài đăng trước khi RecyclerView tái sử dụng.
        holder.txtAmenitiesSummary.setText("Tiện nghi: Đang tải...");

        List<String> names = new ArrayList<>();
        boolean allCached = true;

        for (String id : validIds) {
            String name = amenityNameCache.get(id);

            if (name == null) {
                allCached = false;
                loadAmenityName(id);
            } else {
                names.add(name);
            }
        }

        if (allCached) {
            holder.txtAmenitiesSummary.setText(
                    "Tiện nghi: " + android.text.TextUtils.join(", ", names)
            );
        }
    }

    private void loadAmenityName(String amenityId) {
        if (amenityNameCache.containsKey(amenityId)
                || !loadingAmenityIds.add(amenityId)) {
            return;
        }

        db.collection("amenities")
                .document(amenityId)
                .get()
                .addOnSuccessListener(document -> {
                    String name = null;

                    if (document.exists()) {
                        name = document.getString("name");
                    }

                    // Nếu document không có tên, giữ ID để không mất thông tin.
                    if (name == null || name.trim().isEmpty()) {
                        name = amenityId;
                    }

                    amenityNameCache.put(amenityId, name.trim());
                    loadingAmenityIds.remove(amenityId);

                    // Cập nhật những card đang sử dụng tiện nghi này.
                    notifyPostsUsingAmenity(amenityId);
                })
                .addOnFailureListener(error -> {
                    // Nếu tải thất bại, hiển thị ID thay vì làm mất thông tin.
                    amenityNameCache.put(amenityId, amenityId);
                    loadingAmenityIds.remove(amenityId);

                    notifyPostsUsingAmenity(amenityId);
                });
    }

    private void notifyPostsUsingAmenity(String amenityId) {
        if (postList == null) {
            return;
        }

        for (int i = 0; i < postList.size(); i++) {
            ListingPost post = postList.get(i);

            if (post == null || post.getAmenityIds() == null) {
                continue;
            }

            for (String id : post.getAmenityIds()) {
                if (id != null && amenityId.equals(id.trim())) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return postList != null ? postList.size() : 0;
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        ImageView imgPostCover;
        TextView txtPostTitle;
        TextView txtBedBathSummary;
        TextView txtAmenitiesSummary;
        TextView txtCurrentPrice;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            imgPostCover = itemView.findViewById(R.id.imgPostCover);
            txtPostTitle = itemView.findViewById(R.id.txtPostTitle);
            txtBedBathSummary =
                    itemView.findViewById(R.id.txtBedBathSummary);
            txtAmenitiesSummary =
                    itemView.findViewById(R.id.txtAmenitiesSummary);
            txtCurrentPrice =
                    itemView.findViewById(R.id.txtCurrentPrice);
        }
    }
}
