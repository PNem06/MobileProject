package com.example.mobileproject.model;

public class User extends Account {

    private double averageRating;
    private int totalExchanges;

    // Constructor rỗng
    public User() {
        super();
        this.averageRating = 0.0;
        this.totalExchanges = 0;
    }

    // Constructor đầy đủ
    public User(
            String accountId,
            String username,
            String passwordHash,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            String googleUid,
            AccountStatus status,
            double averageRating,
            int totalExchanges
    ) {
        super(
                accountId,
                username,
                passwordHash,
                fullName,
                email,
                phone,
                avatarUrl,
                googleUid,
                status
        );

        this.averageRating = averageRating;
        this.totalExchanges = totalExchanges;
    }

    // =========================
    // PHƯƠNG THỨC NGHIỆP VỤ
    // =========================

    public void increaseTotalExchanges() {
        totalExchanges++;
    }

    /**
     * Cập nhật điểm trung bình sau khi User nhận thêm một Review.
     *
     * @param newRating điểm mới từ 1 đến 5
     * @param previousReviewCount số review trước khi nhận review mới
     */
    public void updateAverageRating(
            double newRating,
            int previousReviewCount
    ) {
        if (newRating < 1.0 || newRating > 5.0) {
            throw new IllegalArgumentException(
                    "Rating phải nằm trong khoảng từ 1 đến 5."
            );
        }

        if (previousReviewCount < 0) {
            throw new IllegalArgumentException(
                    "Số lượng review không được âm."
            );
        }

        double oldTotalRating =
                averageRating * previousReviewCount;

        averageRating =
                (oldTotalRating + newRating)
                        / (previousReviewCount + 1);
    }

    // =========================
    // GETTER / SETTER
    // =========================

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public int getTotalExchanges() {
        return totalExchanges;
    }

    public void setTotalExchanges(int totalExchanges) {
        this.totalExchanges = totalExchanges;
    }
}