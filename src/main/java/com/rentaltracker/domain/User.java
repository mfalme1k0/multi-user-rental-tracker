package com.rentaltracker.domain;

import java.time.LocalDateTime;

// An application account. The password component contains a password hash,never a plaintext password.
public record User(Long id, String username, String password, LocalDateTime createdAt) {

    // A user that has not been saved yet. The supplied value must already be a hash.
    public static User newUser(String username, String passwordHash) {
        return new User(null, username, passwordHash, null);
    }


     //Legacy factory retained only so callers fail at the persistence boundary rather than
     //silently creating an account without credentials. New code must supply a password hash.
    @Deprecated
    public static User newUser(String username) {
        return newUser(username, null);
    }
}
