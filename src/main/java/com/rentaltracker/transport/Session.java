package com.rentaltracker.transport;

import com.rentaltracker.domain.User;


final class Session {

    private User currentUser;

    boolean isLoggedIn() {
        return currentUser != null;
    }

    void logIn(User user) {
        if (user == null || user.id() == null) {
            throw new IllegalArgumentException("A persisted user is required");
        }
        this.currentUser = user;
    }

    void logOut() {
        this.currentUser = null;
    }

    User currentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No user is logged in");
        }
        return currentUser;
    }

    long userId() {
        return currentUser().id();
    }
}
