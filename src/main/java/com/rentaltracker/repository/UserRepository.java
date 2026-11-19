package com.rentaltracker.repository;

import com.rentaltracker.domain.User;

import java.util.Optional;

/**
 * Persistence contract for users. Every method may throw a DatabaseException subtype; implementations translate
 * SQLException, callers never see it.
 */
public interface UserRepository {

    // return the saved user with id and createdAt filled in.
    User insert(User user);

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);
    User getById(long id);
    Optional<User> findFirst();
}
