package com.example.mobileproject.model;

public abstract class Account {

    private String accountId;
    private String username;

    /*
     * Chỉ giữ để đồng bộ với Class Diagram.
     * KHÔNG lưu password thật hoặc password hash trong app.
     * Firebase Authentication sẽ quản lý mật khẩu.
     */
    private transient String passwordHash;

    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String googleUid;
    private AccountStatus status;

    private AccountRole role;


    // Constructor rỗng
    public Account() {
        this.status = AccountStatus.ACTIVE;
    }

    // Constructor đầy đủ
    public Account(
            String accountId,
            String username,
            String passwordHash,
            String fullName,
            String email,
            String phone,
            String avatarUrl,
            String googleUid,
            AccountStatus status,
            AccountRole role
    ) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
        this.googleUid = googleUid;
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.role = role;
    }

    // =========================
    // PHƯƠNG THỨC NGHIỆP VỤ
    // =========================

    public void updateProfile(
            String fullName,
            String phone,
            String avatarUrl
    ) {
        this.fullName = fullName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    // =========================
    // GETTER / SETTER
    // =========================

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getGoogleUid() {
        return googleUid;
    }

    public void setGoogleUid(String googleUid) {
        this.googleUid = googleUid;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    public AccountRole getRole() {
        return role;
    }

    public void setRole(AccountRole role) {
        this.role = role;
    }
}