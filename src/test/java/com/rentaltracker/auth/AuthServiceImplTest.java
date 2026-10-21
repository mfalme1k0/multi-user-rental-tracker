package com.rentaltracker.auth;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.UniqueConstraintException;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.validation.AuthValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private AuthValidator authValidator;

    private AuthServiceImpl authService;

//    @BeforeEach
//    void setUp() {
//        authService = new AuthServiceImpl(
//                userRepository, passwordHasher, authValidator);
//    }

    @Test
    void testRegisterCreatesUserWithHashedPassword() {
        String username = "alex";
        String password = "SecurePass123";
        String passwordHash = "bcrypt-hash";

        User savedUser = new User(1L, username, passwordHash, null);

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(passwordHasher.hashPassword(password)).thenReturn(passwordHash);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class))).thenReturn(savedUser);

        User result = authService.register(username, password);

        assertSame(savedUser, result);
        assertEquals(username, result.username());
        assertEquals(passwordHash, result.password());
        assertNotEquals(password, result.password());

        verify(userRepository).insert(argThat(user ->
                user.username().equals(username)
                        && user.password().equals(passwordHash)));
    }

    @Test
    void testRegisterRejectsExistingUsername() {
        String username = "alex";
        String password = "SecurePass123";
        User existingUser = User.newUser(username, "existing-hash");

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(existingUser));

        assertThrows(BusinessRuleException.class,
                () -> authService.register(username, password));

        verify(userRepository, never()).insert(any(User.class));
        verify(passwordHasher, never()).hashPassword(anyString());
    }

    @Test
    void testRegisterTranslatesUniqueConstraintException() {
        String username = "alex";
        String password = "SecurePass123";

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(passwordHasher.hashPassword(password)).thenReturn("bcrypt-hash");
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class)))
                .thenThrow(new UniqueConstraintException("username already exists"));

        assertThrows(BusinessRuleException.class,
                () -> authService.register(username, password));
    }

    @Test
    void testRegisterValidatesPasswordBeforeSaving() {
        String username = "alex";
        String password = "short";

        when(authValidator.validateUsername(username)).thenReturn(username);
        doThrow(new com.rentaltracker.exception.ValidationException(
                "Password must be at least 8 characters long"))
                .when(authValidator).validatePassword(password);

        assertThrows(com.rentaltracker.exception.ValidationException.class,
                () -> authService.register(username, password));

        verify(userRepository, never()).insert(any(User.class));
        verify(passwordHasher, never()).hashPassword(anyString());
    }

    @Test
    void testLoginReturnsUserForCorrectCredentials() {
        String username = "alex";
        String password = "SecurePass123";
        User user = User.newUser(username, "bcrypt-hash");

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(password, user.password())).thenReturn(true);

        User result = authService.login(username, password);

        assertSame(user, result);
    }

    @Test
    void testLoginRejectsIncorrectPassword() {
        String username = "alex";
        String password = "WrongPass123";
        User user = User.newUser(username, "bcrypt-hash");

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordHasher.matches(password, user.password())).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> authService.login(username, password));
    }

    @Test
    void testLoginRejectsUnknownUsername() {
        String username = "unknown";

        when(authValidator.validateUsername(username)).thenReturn(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> authService.login(username, "SecurePass123"));

        verify(passwordHasher, never()).matches(anyString(), anyString());
    }

    @Test
    void testLoginRejectsBlankPassword() {
        String username = "alex";

        when(authValidator.validateUsername(username)).thenReturn(username);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> authService.login(username, "  "));

        assertEquals("Invalid username or password", exception.getMessage());
        verify(userRepository, never()).findByUsername(anyString());
    }
}