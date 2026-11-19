package com.rentaltracker.transport;

import com.rentaltracker.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @Test
    void startsLoggedOut() {
        Session session = new Session();
        assertFalse(session.isLoggedIn());
    }

    @Test
    void logInAcceptsPersistedUser() {
        Session session = new Session();
        User user = new User(7L, "alice", "hash", LocalDateTime.now());

        session.logIn(user);

        assertTrue(session.isLoggedIn());
        assertEquals(user, session.currentUser());
        assertEquals(7L, session.userId());
    }

    @Test
    void logInRejectsNullUser() {
        Session session = new Session();
        assertThrows(IllegalArgumentException.class, () -> session.logIn(null));
    }

    @Test
    void logInRejectsUserWithoutId() {
        Session session = new Session();
        User unsaved = User.newUser("bob", "hash");
        assertThrows(IllegalArgumentException.class, () -> session.logIn(unsaved));
    }

    @Test
    void logOutClearsCurrentUser() {
        Session session = new Session();
        session.logIn(new User(1L, "alice", "hash", LocalDateTime.now()));

        session.logOut();

        assertFalse(session.isLoggedIn());
        assertThrows(IllegalStateException.class, session::currentUser);
        assertThrows(IllegalStateException.class, session::userId);
    }

    @Test
    void currentUserWhenLoggedOutThrows() {
        Session session = new Session();
        assertThrows(IllegalStateException.class, session::currentUser);
    }
}