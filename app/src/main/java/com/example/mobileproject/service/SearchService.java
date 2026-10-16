
package com.example.mobileproject.service;

import com.example.mobileproject.model.Post;
import com.example.mobileproject.model.SearchCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SearchService {

    public List<Post> searchPosts(
            List<Post> allPosts,
            SearchCriteria criteria) {

        List<Post> results = new ArrayList<>();

        if (allPosts == null || criteria == null) {
            return results;
        }

        String location = criteria.getLocation();

        if (location == null) {
            location = "";
        }

        location = location.trim().toLowerCase();

        Set<String> selectedAmenities =
                criteria.getAmenityIds();

        for (Post post : allPosts) {

            if (post == null) {
                continue;
            }

            // Chỉ tìm bài đăng đã được duyệt
            if (!Post.APPROVED.equals(post.getStatus())) {
                continue;
            }

            // Kiểm tra địa điểm
            String address = post.getAddressText();

            if (!location.isEmpty()) {
                if (address == null
                        || !address.toLowerCase()
                        .contains(location)) {
                    continue;
                }
            }

            // Kiểm tra số khách
            if (post.getMaxGuests() < criteria.getGuests()) {
                continue;
            }

            // Kiểm tra ngày ở
            if (criteria.getCheckIn() != null
                    && criteria.getCheckOut() != null) {

                if (!post.isWithinAvailablePeriod(
                        criteria.getCheckIn(),
                        criteria.getCheckOut())) {
                    continue;
                }
            }

            // Kiểm tra tiện nghi
            List<String> postAmenities =
                    post.getAmenityIds();

            if (!postAmenities.containsAll(selectedAmenities)) {
                continue;
            }

            results.add(post);
        }

        return results;
    }
}
