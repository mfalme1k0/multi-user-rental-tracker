package com.rentaltracker.auth;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.exception.ValidationException;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.validation.AuthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuthValidator authValidator;

    @Mock
    private Transactor transactor;

    @Mock
    private AppLogger logger;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                passwordHasher,
                authValidator,
                transactor,
                logger
        );

        lenient().when(transactor.inTransaction(
                org.mockito.ArgumentMatchers.<Supplier<User>>any()
        )).thenAnswer(invocation -> {
            Supplier<User> work = invocation.getArgument(0);
            return work.get();
        });
    }

    @Test
    void testRegisterCreatesUserWithValidatedUsernameAndHashedPassword() {
        String username = "  jack  ";
        String cleanUsername = "jack";
        String password = "password123";
        String passwordHash = "bcrypt-hash";

        User registeredUser = mock(User.class);

        when(authValidator.validateUsername(username)).thenReturn(cleanUsername);
        doNothing().when(authValidator).validatePassword(password);
        when(passwordHasher.hashPassword(password)).thenReturn(passwordHash);
        when(userRepository.findByUsername(cleanUsername)).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class))).thenReturn(registeredUser);
        when(registeredUser.id()).thenReturn(1L);

        User result = authService.register(username, password);

        assertSame(registeredUser, result);
        verify(userRepository).findByUsername(cleanUsername);
        verify(userRepository).insert(argThat(user ->
                user.username().equals(cleanUsername)
                        && user.password().equals(passwordHash)
        ));
        verify(transactor).inTransaction(ArgumentMatchers.<Supplier<User>>any());
        verify(logger).info(eq(1L), eq("auth.registration_succeeded"), anyMap());
    }

    @Test
    void testRegisterRejectsDuplicateUsername() {
        String username = "jack";
        String password = "password123";

        User existingUser = mock(User.class);

        when(authValidator.validateUsername(username)).thenReturn(username);
        doNothing().when(authValidator).validatePassword(password);
        when(passwordHasher.hashPassword(password)).thenReturn("bcrypt-hash");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));

        assertThrows(
                BusinessRuleException.class,
                () -> authService.register(username, password)
        );

        verify(userRepository, never()).insert(any(User.class));
        verify(logger).warn(isNull(), eq("auth.registration_rejected"), anyMap());
    }

    @Test
    void testRegisterRejectsInvalidUsername() {
        String username = " ";
        String password = "password123";

        when(authValidator.validateUsername(username))
                .thenThrow(new ValidationException("Username must not be blank"));

        assertThrows(
                ValidationException.class,
                () -> authService.register(username, password)
        );

        verifyNoInteractions(passwordHasher, userRepository, transactor);
        verify(logger).warn(isNull(), eq("validation.failed"), anyMap());
    }

    @Test
    void testRegisterRejectsInvalidPassword() {
        String username = "jack";
        String password = "short";

        when(authValidator.validateUsername(username)).thenReturn(username);
        doThrow(new ValidationException("Password must be at least 8 characters long"))
                .when(authValidator).validatePassword(password);

        assertThrows(
                ValidationException.class,
                () -> authService.register(username, password)
        );

        verifyNoInteractions(passwordHasher, userRepository, transactor);
        verify(logger).warn(isNull(), eq("validation.failed"), anyMap());
    }

    @Test
    void testRegisterConvertsUniqueConstraintExceptionToBusinessRuleException() {
        String username = "jack";
        String password = "password123";

        when(authValidator.validateUsername(username)).thenReturn(username);
        doNothing().when(authValidator).validatePassword(password);
        when(passwordHasher.hashPassword(password)).thenReturn("bcrypt-hash");
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class)))
                .thenThrow(new UniqueConstraintException("Duplicate username"));

        assertThrows(
                BusinessRuleException.class,
                () -> authService.register(username, password)
        );

        verify(logger).warn(isNull(), eq("auth.registration_rejected"), anyMap());
    }

    @Test
    void testLoginReturnsUserWhenCredentialsAreValid() {
        String username = "jack";
        String password = "password123";
        User user = mock(User.class);

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.password()).thenReturn("bcrypt-hash");
        when(passwordHasher.matches(password, "bcrypt-hash")).thenReturn(true);
        when(user.id()).thenReturn(1L);

        User result = authService.login(username, password);

        assertSame(user, result);
        verify(logger).info(eq(1L), eq("auth.login_succeeded"), anyMap());
    }

    @Test
    void testLoginRejectsIncorrectPassword() {
        String username = "jack";
        String password = "wrongpassword";
        User user = mock(User.class);

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(user.password()).thenReturn("bcrypt-hash");
        when(passwordHasher.matches(password, "bcrypt-hash")).thenReturn(false);

        assertThrows(
                BusinessRuleException.class,
                () -> authService.login(username, password)
        );

        verify(logger).warn(isNull(), eq("auth.login_rejected"), anyMap());
    }

    @Test
    void testLoginRejectsUnknownUsername() {
        String username = "unknown";
        String password = "password123";

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(
                BusinessRuleException.class,
                () -> authService.login(username, password)
        );
    }

    @Test
    void testLoginRejectsNullOrBlankCredentials() {
        assertThrows(BusinessRuleException.class, () -> authService.login(null, "password123"));
        assertThrows(BusinessRuleException.class, () -> authService.login("jack", null));
        assertThrows(BusinessRuleException.class, () -> authService.login(" ", "password123"));
        assertThrows(BusinessRuleException.class, () -> authService.login("jack", " "));
    }

    @Test
    void testLoginRejectsInvalidUsernameFromValidator() {
        String username = "jack";
        String password = "password123";

        when(authValidator.validateUsername(username))
                .thenThrow(new ValidationException("Invalid username"));

        assertThrows(
                BusinessRuleException.class,
                () -> authService.login(username, password)
        );

        verify(logger).warn(isNull(), eq("auth.login_rejected"), anyMap());
        verifyNoInteractions(passwordHasher);
    }
}