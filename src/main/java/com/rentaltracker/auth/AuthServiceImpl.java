package com.rentaltracker.auth;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.validation.AuthValidator;

import java.util.Objects;

public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthValidator authValidator;
    private final Transactor transactor;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordHasher passwordHasher, AuthValidator authValidator, Transactor transactor) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.authValidator = Objects.requireNonNull(authValidator);
        this.transactor = Objects.requireNonNull(transactor);
    }

    @Override
    public User register(String username, String password) {
        String cleanUsername = authValidator.validateUsername(username);
        authValidator.validatePassword(password);

        // BCrypt is computationally expensive, so hash before opening the transaction.
        String passwordHash = passwordHasher.hashPassword(password);
        User newUser = User.newUser(cleanUsername, passwordHash);

        try {
            return transactor.inTransaction(() -> {
                if (userRepository.findByUsername(cleanUsername).isPresent()) {
                    throw new BusinessRuleException(
                            "That username is already taken");
                }

                return userRepository.insert(newUser);
            });
        } catch (UniqueConstraintException exception) {
            // The unique database constraint also protects against concurrent registrations.
            throw new BusinessRuleException("That username is already taken");
        }
    }


    @Override
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessRuleException("Invalid username or password");
        }

        String cleanUsername = authValidator.validateUsername(username);

        User user = userRepository.findByUsername(cleanUsername)
                .orElseThrow(() -> new BusinessRuleException("Invalid username or password"));

        if (!passwordHasher.matches(password, user.password())) {
            throw new BusinessRuleException("Invalid username or password");
        }

        return user;
    }
}
