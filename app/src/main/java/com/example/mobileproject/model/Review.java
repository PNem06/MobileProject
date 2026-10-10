package com.example.mobileproject.model;

import android.util.Log;

import com.google.firebase.Firebase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Review {
    private String reviewId;
    private String exchangeId;
    private String reviewerId;
    private String revieweeId;
    private double rating;
    private String comment;

    public Review(String reviewId, String exchangeId, String reviewerId, String revieweeId, double rating, String comment) {
        this.reviewId = reviewId;
        this.exchangeId = exchangeId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rating = rating;
        this.comment = comment;
    }
    public void submit() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("reviewId", reviewId);
        data.put("exchangeId", exchangeId);
        data.put("reviewerId", reviewerId);
        data.put("revieweeId", revieweeId);
        data.put("rating", rating);
        data.put("comment", comment);

        db.collection("reviews")
                .document(reviewId)
                .set(data)
                .addOnSuccessListener(unused -> {
                    android.util.Log.d(
                            "Review",
                            "Review submitted successfully"
                    );
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(
                            "Review",
                            "Failed to submit review",
                            e
                    );
                });
    }
    //Sửa
    public void edit(String comment, double rating){
        this.comment=comment;
        this.rating=rating;
    }
    //Save vô firebase
    public void updateInDatabase() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> updates = new HashMap<>();
        updates.put("comment", comment);
        updates.put("rating", rating);

        db.collection("reviews")
                .document(reviewId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        android.util.Log.d(
                                "Review",
                                "Review updated successfully"
                        )
                )
                .addOnFailureListener(e ->
                        android.util.Log.e(
                                "Review",
                                "Failed to update review",
                                e
                        )
                );
    }
}

