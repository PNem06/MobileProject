package com.example.mobileproject.service;

import com.example.mobileproject.model.User;

public class AuthService {

    public AuthService() {
    }

    public User registerWithPassword(
            String username,
            String password,
            String email
    ) {
        // TODO: Firebase Authentication sẽ được tích hợp sau
        return null;
    }

    public User signInWithPassword(
            String username,
            String password
    ) {
        // TODO: Xử lý đăng nhập ở giai đoạn Authentication
        return null;
    }

    public User signInWithGoogle(String idToken) {
        // TODO: Tích hợp Google/Firebase sau
        return null;
    }

    public void signOut() {
        // TODO: Xử lý đăng xuất sau
    }
}