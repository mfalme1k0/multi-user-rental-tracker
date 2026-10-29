package com.rentaltracker.auth;

import com.rentaltracker.domain.User;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.validation.AuthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplAvailabilityTest {

    private UserRepository userRepository;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = new AuthServiceImpl(
                userRepository,
                mock(PasswordHasher.class),
                mock(AuthValidator.class),
                mock(Transactor.class),
                mock(AppLogger.class));
    }

    @Test
    void usernameIsAvailableWhenNoUserHasIt() {
        when(userRepository.findByUsername("fresh")).thenReturn(Optional.empty());

        assertTrue(authService.isUsernameAvailable("fresh"));
    }

    @Test
    void usernameIsUnavailableWhenAUserAlreadyHasIt() {
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(mock(User.class)));

        assertFalse(authService.isUsernameAvailable("taken"));
    }
}
