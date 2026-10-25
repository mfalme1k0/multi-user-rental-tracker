package com.rentaltracker.transport;

import com.rentaltracker.auth.AuthService;
import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemDetails;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Page;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTest {

    @Mock AuthService authService;
    @Mock ItemService itemService;
    @Mock RentalService rentalService;
    @Mock AppLogger logger;

    @Test
    void exitFromAuthMenuPrintsGoodbye() {
        String output = run("3\n");
        assertTrue(output.contains("Goodbye."));
        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    void loginThenExit() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);

        String output = run("1\nalice\nsecret\n5\n");

        assertTrue(output.contains("Welcome back, alice!"));
        assertTrue(output.contains("Goodbye."));
        verify(authService).login("alice", "secret");
    }

    @Test
    void browseShowsEmptyMarketplaceMessage() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(), 0, ListScreen.PAGE_SIZE, false));

        String output = run("1\nalice\nsecret\n3\n5\n");

        assertTrue(output.contains("No listings from other users are available."));
    }

    @Test
    void browseSelectsMarketplaceItemUsingServicePaging() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        Item listing = new Item(
                10L, 2L, "Drill", "cordless", new BigDecimal("5.00"),
                ItemStatus.AVAILABLE, LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);
        when(itemService.getMarketplaceListings(eq(1L), eq(0), eq(ListScreen.PAGE_SIZE)))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L))
                .thenReturn(new ItemDetails(listing, "bob"));

        // login, browse, pick 1, back from detail, exit
        String output = run("1\nalice\nsecret\n3\n1\n2\n5\n");

        assertTrue(output.contains("Drill"));
        assertTrue(output.contains("bob"));
        verify(itemService, atLeastOnce())
                .getMarketplaceListings(eq(1L), eq(0), eq(ListScreen.PAGE_SIZE));
    }

    @Test
    void guardedPrintsBusinessRuleMessage() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);
        when(itemService.listItem(eq(1L), anyString(), anyString(), any()))
                .thenThrow(new BusinessRuleException("Name is required"));

        String output = run("1\nalice\nsecret\n1\n\ndesc\n3.00\n5\n");

        assertTrue(output.contains("Name is required"));
    }

    @Test
    void guardedLogsNotFoundAndShowsFriendlyMessage() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);
        when(itemService.getInventory(1L)).thenThrow(new NotFoundException("gone"));

        String output = run("1\nalice\nsecret\n2\n5\n");

        assertTrue(output.contains("That record no longer exists."));
        verify(logger).warn(eq(1L), eq("item.inventory.not_found"), any(Map.class));
    }

    @Test
    void guardedLogsDatabaseError() {
        User user = new User(1L, "alice", "hash", LocalDateTime.now());
        when(authService.login("alice", "secret")).thenReturn(user);
        when(itemService.getInventory(1L)).thenThrow(new DatabaseException("disk"));

        String output = run("1\nalice\nsecret\n2\n5\n");

        assertTrue(output.contains("Something went wrong while accessing your data"));
        verify(logger).error(
                eq(1L),
                eq("item.inventory.database_error"),
                any(Map.class),
                any(DatabaseException.class));
    }

    private String run(String scriptedInput) {
        ByteArrayInputStream in =
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleIO io = new ConsoleIO(in, new PrintStream(out, true, StandardCharsets.UTF_8));
        new Transport(authService, itemService, rentalService, io, logger).run();
        return out.toString(StandardCharsets.UTF_8);
    }
}