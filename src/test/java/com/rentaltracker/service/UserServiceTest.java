package com.rentaltracker.service;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void testGetUserByIdReturnsUserFromRepository() {
        User expectedUser = org.mockito.Mockito.mock(User.class);
        when(userRepository.getById(USER_ID)).thenReturn(expectedUser);

        User result = userService.getUserById(USER_ID);

        assertSame(expectedUser, result);
        verify(userRepository).getById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void testGetUserByIdPropagatesNotFoundException() {
        NotFoundException exception = new NotFoundException("User not found");
        when(userRepository.getById(USER_ID)).thenThrow(exception);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> userService.getUserById(USER_ID)
        );

        assertSame(exception, thrown);
        verify(userRepository).getById(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }
}