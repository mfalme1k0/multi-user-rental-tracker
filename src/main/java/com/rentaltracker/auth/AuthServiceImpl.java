package com.rentaltracker.auth;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.validation.AuthValidator;

import java.util.Map;
import java.util.Objects;

public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthValidator authValidator;
    private final Transactor transactor;
    private final AppLogger logger;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordHasher passwordHasher, AuthValidator authValidator, Transactor transactor, AppLogger logger) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.authValidator = Objects.requireNonNull(authValidator);
        this.transactor = Objects.requireNonNull(transactor);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public User register(String username, String password) {
        String cleanUsername;

        try {
            cleanUsername = authValidator.validateUsername(username);
            authValidator.validatePassword(password);
        } catch (ValidationException exception) {
            logger.warn(
                    null,
                    "validation.failed",
                    Map.of(
                            "entity", "User",
                            "operation", "register",
                            "reason", exception.getMessage()
                    )
            );

            throw exception;
        }

        // BCrypt is computationally expensive, so hash before opening the transaction.
        String passwordHash = passwordHasher.hashPassword(password);
        User newUser = User.newUser(cleanUsername, passwordHash);

        try {
            User registeredUser = transactor.inTransaction(() -> {
                if (userRepository.findByUsername(cleanUsername).isPresent()) {
                    throw new BusinessRuleException("That username is already taken");
                }

                return userRepository.insert(newUser);
            });

            // Log success only after the transaction completes.
            logger.info(
                    registeredUser.id(),
                    "auth.registration_succeeded",
                    Map.of("reason", "registration_completed")
            );

            return registeredUser;

        } catch (BusinessRuleException exception) {
            logger.warn(
                    null,
                    "auth.registration_rejected",
                    Map.of("reason", "username_unavailable")
            );

            throw exception;

        } catch (UniqueConstraintException exception) {
            // Handles a concurrent registration taking the username after the initial availability check.
            logger.warn(
                    null,
                    "auth.registration_rejected",
                    Map.of("reason", "username_unavailable")
            );

            throw new BusinessRuleException("That username is already taken");
        }
    }

    @Override
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw invalidLogin();
        }

        String cleanUsername;

        try {
            cleanUsername = authValidator.validateUsername(username);
        } catch (ValidationException exception) {
            logger.warn(null,
                    "auth.login_rejected",
                    Map.of("reason", "invalid_credentials")
            );

            throw new BusinessRuleException("Invalid username or password");
        }

        User user = userRepository.findByUsername(cleanUsername)
                .orElseThrow(this::invalidLogin);

        if (!passwordHasher.matches(password, user.password())) {
            throw invalidLogin();
        }

        logger.info(
                user.id(),
                "auth.login_succeeded",
                Map.of("reason", "credentials_validated")
        );

        return user;
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        return userRepository.findByUsername(username).isEmpty();
    }

    private BusinessRuleException invalidLogin() {
        logger.warn(null,
                "auth.login_rejected",
                Map.of("reason", "invalid_credentials")
        );

        return new BusinessRuleException("Invalid username or password");
    }
}
