package com.rentaltracker.auth;

import com.rentaltracker.domain.User;

public interface AuthService {
    User register(String username, String password);

    User login(String username, String password);

    boolean isUsernameAvailable(String username);
}
