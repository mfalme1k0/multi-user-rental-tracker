package com.rentaltracker.service;

import com.rentaltracker.domain.User;
import com.rentaltracker.repository.UserRepository;

import java.util.Objects;
import java.util.Optional;

// Account rules.
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    // The app owner, if the account has already been set up.
    public Optional<User> findOwner() {
        return users.findFirst();
    }

    // First launch: validates and stores the owner account.
    public User registerOwner(String username) {
        String clean = Validation.requireNonBlank(username, "username");
        return users.insert(User.newUser(clean));
    }

    // Reuses the account with this username if one exists, otherwise creates it.
    public User findOrCreateRenter(String username) {
        String clean = Validation.requireNonBlank(username, "username");
        return users.findByUsername(clean).orElseGet(() -> users.insert(User.newUser(clean)));
    }
}
