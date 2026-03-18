package com.example.musiclibrary.session;

import com.example.musiclibrary.model.User;

public final class SessionManager {

    private static User currentUser;

    private SessionManager() {
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static boolean isAdmin() {
        return currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.getRole());
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}

