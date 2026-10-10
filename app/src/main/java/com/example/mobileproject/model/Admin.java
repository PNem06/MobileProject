package com.example.mobileproject.model;

import java.util.ArrayList;
import java.util.List;

public class Admin extends Account {

        private List<User> users;
        private List<Post> posts;
        private List<Exchange> exchanges;
        private List<Report> reports;

        public Admin() {
            super();
            users = new ArrayList<>();
            posts = new ArrayList<>();
            exchanges = new ArrayList<>();
            reports = new ArrayList<>();
        }

        // Constructor đầy đủ
        public Admin(
                String accountId,
                String username,
                String passwordHash,
                String fullName,
                String email,
                String phone,
                String avatarUrl,
                String googleUid,
                AccountStatus status
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
                    status,
                    AccountRole.ADMIN
            );

            users = new ArrayList<>();
            posts = new ArrayList<>();
            exchanges = new ArrayList<>();
            reports = new ArrayList<>();
        }

        public List<User> getUsers() {
            return new ArrayList<>(users);
        }

        public void lockUser(String userId) {
            User user = findUser(userId);

            if (user != null) {
                user.setStatus(AccountStatus.LOCKED);
            }
        }

        public void unlockUser(String userId) {
            User user = findUser(userId);

            if (user != null) {
                user.setStatus(AccountStatus.ACTIVE);
            }
        }

        public void approvePost(String postId) {
            Post post = findPost(postId);

            if (post != null && Post.PENDING.equals(post.getStatus())) {
                post.setStatus(Post.APPROVED);
            }
        }

        public void rejectPost(String postId) {
            Post post = findPost(postId);

            if (post != null && Post.PENDING.equals(post.getStatus())) {
                post.setStatus(Post.REJECTED);
            }
        }

        public void deletePost(String postId) {
            Post post = findPost(postId);

            if (post != null) {
                posts.remove(post);
            }
        }

        public List<Exchange> viewExchanges() {
            return new ArrayList<>(exchanges);
        }

        // Xử lý yêu cầu hủy trao đổi có thanh toán
        public void processPaidCancellation(String exchangeId) {
            for (Exchange exchange : exchanges) {
                if (exchange != null
                        && exchangeId != null
                        && exchangeId.equals(exchange.getExchangeId())) {

                    // Bổ sung xử lý hủy và hoàn tiền
                    // theo các phương thức của class Exchange và Payment.
                    return;
                }
            }
        }

        public void resolveReport(String reportId, String action) {
            for (Report report : reports) {
                if (report != null
                        && reportId != null
                        && reportId.equals(report.getReportId())) {

                    report.resolved(action);
                    return;
                }
            }
        }

        public DashboardStats getDashboardStats() {
            long totalUsers = 0;
            long totalPosts = 0;
            long totalExchanges = 0;
            long pendingReports = 0;

            for (User user : users) {
                if (user != null) {
                    totalUsers++;
                }
            }

            for (Post post : posts) {
                if (post != null) {
                    totalPosts++;
                }
            }

            for (Exchange exchange : exchanges) {
                if (exchange != null) {
                    totalExchanges++;
                }
            }

            for (Report report : reports) {
                if (report != null
                        && report.getStatus() == ReportStatus.PENDING) {
                    pendingReports++;
                }
            }

            return new DashboardStats(
                    totalUsers,
                    totalPosts,
                    totalExchanges,
                    pendingReports
            );
        }

        private User findUser(String userId) {
            if (userId == null) {
                return null;
            }

            for (User user : users) {
                if (user != null
                        && userId.equals(user.getAccountId())) {
                    return user;
                }
            }

            return null;
        }

        private Post findPost(String postId) {
            if (postId == null) {
                return null;
            }

            for (Post post : posts) {
                if (post != null
                        && postId.equals(post.getPostId())) {
                    return post;
                }
            }

            return null;
        }

        public void setUsers(List<User> users) {
            this.users = users == null
                    ? new ArrayList<>()
                    : new ArrayList<>(users);
        }

        public void setPosts(List<Post> posts) {
            this.posts = posts == null
                    ? new ArrayList<>()
                    : new ArrayList<>(posts);
        }

        public void setExchanges(List<Exchange> exchanges) {
            this.exchanges = exchanges == null
                    ? new ArrayList<>()
                    : new ArrayList<>(exchanges);
        }

        public void setReports(List<Report> reports) {
            this.reports = reports == null
                    ? new ArrayList<>()
                    : new ArrayList<>(reports);
        }
    }

