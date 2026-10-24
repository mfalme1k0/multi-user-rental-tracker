package com.rentaltracker.service;

import com.rentaltracker.domain.User;
import com.rentaltracker.repository.UserRepository;

import java.util.Objects;

// Account rules.
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    // Retrieves an existing user by their ID.
    public User getUserById(long userId) {
        return userRepository.getById(userId);
    }
}
