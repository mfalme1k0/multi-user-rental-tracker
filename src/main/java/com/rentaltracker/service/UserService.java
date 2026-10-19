package com.rentaltracker.service;

import com.rentaltracker.domain.User;
import com.rentaltracker.repository.UserRepository;

import java.util.Objects;
import java.util.Optional;

// Account rules.
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    // The app owner, if the account has already been set up.
    public Optional<User> findOwner() {
        return userRepository.findFirst();
    }

    // First launch: validates and stores the owner account.
    public User registerOwner(String username) {
        String clean = Validation.requireNonBlank(username, "username");

        return userRepository.insert(User.newUser(clean));
    }

    // Reuses the account with this username if one exists, otherwise creates it.
    public User findOrCreateRenter(String username) {
        String clean = Validation.requireNonBlank(username, "username");

        return userRepository.findByUsername(clean)
                    .orElseGet(() -> userRepository.insert(User.newUser(clean)));
    }
}
