package com.rentaltracker.transport;

import com.rentaltracker.auth.AuthService;
import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemDetails;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Page;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.InputClosedException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransportTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 12, 0);

    @Mock AuthService authService;
    @Mock ItemService itemService;
    @Mock RentalService rentalService;
    @Mock AppLogger logger;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void constructorsRejectNullDependencies() {
        ConsoleIO consoleIO = io("3\n");
        assertThrows(NullPointerException.class,
                () -> new Transport(null, itemService, rentalService, consoleIO, logger));
        assertThrows(NullPointerException.class,
                () -> new Transport(authService, null, rentalService, consoleIO, logger));
        assertThrows(NullPointerException.class,
                () -> new Transport(authService, itemService, null, consoleIO, logger));
        assertThrows(NullPointerException.class,
                () -> new Transport(authService, itemService, rentalService, null, logger));
        assertThrows(NullPointerException.class,
                () -> new Transport(authService, itemService, rentalService, consoleIO, null));
    }

    @Test
    void packagePrivateConstructorAcceptsSeededSession() {
        Session session = new Session();
        session.logIn(user(1L, "alice"));
        when(itemService.getInventory(1L)).thenReturn(List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleIO consoleIO = new ConsoleIO(
                new ByteArrayInputStream("2\n5\n3\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8));
        new Transport(authService, itemService, rentalService, consoleIO, logger, session).run();

        assertTrue(out.toString(StandardCharsets.UTF_8).contains("No items found."));
    }

    // -------------------------------------------------------------------------
    // Auth menu
    // -------------------------------------------------------------------------

    @Test
    void exitFromAuthMenuPrintsGoodbye() {
        String output = run("3\n");
        assertTrue(output.contains("Goodbye."));
        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    void invalidAuthOptionThenExit() {
        String output = run("9\n3\n");
        assertTrue(output.contains("Invalid option."));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void loginUnknownUsernameStopsBeforePassword() {
        when(authService.isUsernameAvailable("ghost")).thenReturn(true);

        String output = run("1\nghost\n3\n");

        assertTrue(output.contains("Please create an account first."));
        verify(authService, never()).login(anyString(), anyString());
    }

    @Test
    void loginThenLogoutThenExit() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenReturn(user(1L, "alice"));

        // 5 = log out → auth menu; 3 = exit
        String output = run("1\nalice\nsecret\n5\n3\n");

        assertTrue(output.contains("Welcome back, alice!"));
        assertTrue(output.contains("1. Log in"));
        assertTrue(output.contains("Goodbye."));
        verify(authService).login("alice", "secret");
    }

    @Test
    void logoutThenLoginAgain() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenReturn(user(1L, "alice"));

        String output = run("1\nalice\nsecret\n5\n1\nalice\nsecret\n5\n3\n");

        verify(authService, times(2)).login("alice", "secret");
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void registerRejectsTakenUsernameBeforePassword() {
        when(authService.isUsernameAvailable("king1")).thenReturn(false);

        String output = run("2\nking1\n3\n");

        assertTrue(output.contains("invalid username"));
        verify(authService).isUsernameAvailable("king1");
        verify(authService, never()).register(anyString(), anyString());
    }

    @Test
    void registerPasswordMismatch() {
        when(authService.isUsernameAvailable("bob")).thenReturn(true);

        String output = run("2\nbob\npass1\npass2\n3\n");

        assertTrue(output.contains("Passwords do not match."));
        verify(authService, never()).register(anyString(), anyString());
    }

    @Test
    void registerSuccessThenLogoutThenExit() {
        when(authService.isUsernameAvailable("bob")).thenReturn(true);
        when(authService.register("bob", "pass")).thenReturn(user(2L, "bob"));

        String output = run("2\nbob\npass\npass\n5\n3\n");

        assertTrue(output.contains("Account created. Welcome, bob!"));
        verify(authService).register("bob", "pass");
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void eofDuringAuthPrintsGoodbye() {
        assertTrue(run("").contains("Goodbye."));
    }

    // -------------------------------------------------------------------------
    // Main menu
    // -------------------------------------------------------------------------

    @Test
    void invalidMainMenuOption() {
        stubAliceLogin();

        String output = run("1\nalice\nsecret\n9\n5\n3\n");

        assertTrue(output.contains("Invalid option."));
    }

    // -------------------------------------------------------------------------
    // List item
    // -------------------------------------------------------------------------

    @Test
    void listItemSuccess() {
        stubAliceLogin();
        when(itemService.listItem(eq(1L), eq("Drill"), eq("cordless"), any(BigDecimal.class)))
                .thenReturn(item(10L, 1L, "Drill", ItemStatus.AVAILABLE));

        String output = run("1\nalice\nsecret\n1\nDrill\ncordless\n5.00\n5\n3\n");

        assertTrue(output.contains("Item listed successfully (id 10)"));
    }

    @Test
    void listItemInvalidCost() {
        stubAliceLogin();

        String output = run("1\nalice\nsecret\n1\nDrill\ncordless\nabc\n5\n3\n");

        assertTrue(output.contains("Invalid cost."));
        verify(itemService, never()).listItem(anyLong(), anyString(), anyString(), any());
    }

    // -------------------------------------------------------------------------
    // Inventory / showOwnItem
    // -------------------------------------------------------------------------

    @Test
    void inventoryEmpty() {
        stubAliceLogin();
        when(itemService.getInventory(1L)).thenReturn(List.of());

        String output = run("1\nalice\nsecret\n2\n5\n3\n");

        assertTrue(output.contains("No items found."));
    }

    @Test
    void inventoryDelistThenBack() {
        stubAliceLogin();
        Item available = item(10L, 1L, "Drill", ItemStatus.AVAILABLE);
        Item unlisted = item(10L, 1L, "Drill", ItemStatus.UNLISTED);
        when(itemService.getInventory(1L)).thenReturn(List.of(available)).thenReturn(List.of(unlisted));
        when(itemService.getDetails(10L))
                .thenReturn(new ItemDetails(available, "alice"))
                .thenReturn(new ItemDetails(unlisted, "alice"));
        when(itemService.delist(10L, 1L)).thenReturn(unlisted);

        String output = run("1\nalice\nsecret\n2\n1\n1\n2\nB\n5\n3\n");

        assertTrue(output.contains("Item delisted."));
        verify(itemService).delist(10L, 1L);
    }

    @Test
    void inventoryRelist() {
        stubAliceLogin();
        Item unlisted = item(10L, 1L, "Drill", ItemStatus.UNLISTED);
        Item available = item(10L, 1L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getInventory(1L)).thenReturn(List.of(unlisted));
        when(itemService.getDetails(10L))
                .thenReturn(new ItemDetails(unlisted, "alice"))
                .thenReturn(new ItemDetails(available, "alice"));
        when(itemService.relist(10L, 1L)).thenReturn(available);

        String output = run("1\nalice\nsecret\n2\n1\n1\n2\nB\n5\n3\n");

        assertTrue(output.contains("Item relisted."));
        verify(itemService).relist(10L, 1L);
    }

    @Test
    void inventoryInvalidOwnItemOption() {
        stubAliceLogin();
        Item available = item(10L, 1L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getInventory(1L)).thenReturn(List.of(available));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(available, "alice"));

        String output = run("1\nalice\nsecret\n2\n1\n9\n2\nB\n5\n3\n");

        assertTrue(output.contains("Invalid option."));
    }

    @Test
    void ownItemBackWith2() {
        stubAliceLogin();
        Item available = item(10L, 1L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getInventory(1L)).thenReturn(List.of(available));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(available, "alice"));

        run("1\nalice\nsecret\n2\n1\n2\nB\n5\n3\n");
        verify(itemService, never()).delist(anyLong(), anyLong());
    }

    @Test
    void ownItemBackWithB() {
        stubAliceLogin();
        Item available = item(10L, 1L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getInventory(1L)).thenReturn(List.of(available));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(available, "alice"));

        run("1\nalice\nsecret\n2\n1\nb\nB\n5\n3\n");
        verify(itemService, never()).delist(anyLong(), anyLong());
    }

    // -------------------------------------------------------------------------
    // Browse / showListing / rent
    // -------------------------------------------------------------------------

    @Test
    void browseShowsEmptyMarketplaceMessage() {
        stubAliceLogin();
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(), 0, ListScreen.PAGE_SIZE, false));

        String output = run("1\nalice\nsecret\n3\n5\n3\n");

        assertTrue(output.contains("No listings from other users are available."));
    }

    @Test
    void browseBackWhenListingsExistDoesNotPrintEmptyMessage() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));

        String output = run("1\nalice\nsecret\n3\nB\n5\n3\n");

        assertTrue(!output.contains("No listings from other users are available."));
    }

    @Test
    void browseSelectsMarketplaceItemUsingServicePaging() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), eq(0), eq(ListScreen.PAGE_SIZE)))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        String output = run("1\nalice\nsecret\n3\n1\n2\n5\n3\n");

        assertTrue(output.contains("Drill"));
        assertTrue(output.contains("bob"));
        verify(itemService, atLeastOnce())
                .getMarketplaceListings(eq(1L), eq(0), eq(ListScreen.PAGE_SIZE));
    }

    @Test
    void listingBackWith2() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        run("1\nalice\nsecret\n3\n1\n2\n5\n3\n");
        verify(rentalService, never()).recordRental(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listingBackWithB() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        run("1\nalice\nsecret\n3\n1\nb\n5\n3\n");
        verify(rentalService, never()).recordRental(anyLong(), anyLong(), anyInt());
    }

    @Test
    void listingInvalidOption() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        String output = run("1\nalice\nsecret\n3\n1\n9\n5\n3\n");

        assertTrue(output.contains("Invalid option."));
    }

    @Test
    void rentSuccess() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));
        Rental rental = new Rental(99L, 10L, 1L, NOW, NOW.plusDays(3), null, RentalStatus.ACTIVE);
        when(rentalService.recordRental(10L, 1L, 3)).thenReturn(rental);

        String output = run("1\nalice\nsecret\n3\n1\n1\n3\n5\n3\n");

        assertTrue(output.contains("Rental recorded successfully."));
        assertTrue(output.contains("Rental ID: 99"));
        verify(rentalService).recordRental(10L, 1L, 3);
    }

    @Test
    void rentInvalidDuration() {
        stubAliceLogin();
        Item listing = item(10L, 2L, "Drill", ItemStatus.AVAILABLE);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        String output = run("1\nalice\nsecret\n3\n1\n1\nabc\n5\n3\n");

        assertTrue(output.contains("Invalid duration."));
        verify(rentalService, never()).recordRental(anyLong(), anyLong(), anyInt());
    }

    @Test
    void nullDescriptionRendersDash() {
        stubAliceLogin();
        Item listing = new Item(10L, 2L, "Drill", null, new BigDecimal("5.00"),
                ItemStatus.AVAILABLE, null);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        String output = run("1\nalice\nsecret\n3\n1\n2\n5\n3\n");

        assertTrue(output.contains("description    -"));
        assertTrue(output.contains("listed         -"));
    }

    @Test
    void blankDescriptionRendersDash() {
        stubAliceLogin();
        Item listing = new Item(10L, 2L, "Drill", "   ", new BigDecimal("5.00"),
                ItemStatus.AVAILABLE, NOW);
        when(itemService.getMarketplaceListings(eq(1L), anyInt(), anyInt()))
                .thenReturn(new Page<>(List.of(listing), 0, ListScreen.PAGE_SIZE, false));
        when(itemService.getDetails(10L)).thenReturn(new ItemDetails(listing, "bob"));

        String output = run("1\nalice\nsecret\n3\n1\n2\n5\n3\n");

        assertTrue(output.contains("description    -"));
    }

    // -------------------------------------------------------------------------
    // Confirm return / tryConfirmReturn
    // -------------------------------------------------------------------------

    @Test
    void confirmReturnEmpty() {
        stubAliceLogin();
        when(rentalService.getActiveRentals(1L)).thenReturn(List.of());

        String output = run("1\nalice\nsecret\n4\n5\n3\n");

        assertTrue(output.contains("No active rentals."));
    }

    @Test
    void confirmReturnSuccess() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        RentalDetails details = new RentalDetails(rental, "Drill", "bob");
        when(rentalService.getActiveRentals(1L)).thenReturn(List.of(details)).thenReturn(List.of());
        when(rentalService.confirmReturn(5L, 1L)).thenReturn(rental.closedAt(NOW));

        String output = run("1\nalice\nsecret\n4\n1\n1\n5\n3\n");

        assertTrue(output.contains("Return confirmed successfully."));
        verify(rentalService).confirmReturn(5L, 1L);
    }

    @Test
    void confirmReturnBackWith2() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));

        run("1\nalice\nsecret\n4\n1\n2\nB\n5\n3\n");
        verify(rentalService, never()).confirmReturn(anyLong(), anyLong());
    }

    @Test
    void confirmReturnBackWithB() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));

        run("1\nalice\nsecret\n4\n1\nb\nB\n5\n3\n");
        verify(rentalService, never()).confirmReturn(anyLong(), anyLong());
    }

    @Test
    void confirmReturnInvalidOptionThenBack() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));

        String output = run("1\nalice\nsecret\n4\n1\n9\n2\nB\n5\n3\n");

        assertTrue(output.contains("Invalid option."));
    }

    @Test
    void confirmReturnBusinessRuleMessage() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));
        when(rentalService.confirmReturn(5L, 1L)).thenThrow(new BusinessRuleException("Not the owner"));

        String output = run("1\nalice\nsecret\n4\n1\n1\n2\nB\n5\n3\n");

        assertTrue(output.contains("Not the owner"));
    }

    @Test
    void confirmReturnNotFound() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));
        when(rentalService.confirmReturn(5L, 1L)).thenThrow(new NotFoundException("gone"));

        String output = run("1\nalice\nsecret\n4\n1\n1\n2\nB\n5\n3\n");

        assertTrue(output.contains("That record no longer exists."));
        verify(logger).warn(eq(1L), eq("rental.confirm.not_found"), any(Map.class));
    }

    @Test
    void confirmReturnDatabaseError() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));
        when(rentalService.confirmReturn(5L, 1L)).thenThrow(new DatabaseException("disk"));

        String output = run("1\nalice\nsecret\n4\n1\n1\n2\nB\n5\n3\n");

        assertTrue(output.contains("Something went wrong while accessing your data"));
        verify(logger).error(eq(1L), eq("rental.confirm.database_error"),
                any(Map.class), any(DatabaseException.class));
    }

    @Test
    void confirmReturnInputClosedUnwinds() {
        stubAliceLogin();
        Rental rental = new Rental(5L, 10L, 2L, NOW, NOW.plusDays(2), null, RentalStatus.ACTIVE);
        when(rentalService.getActiveRentals(1L))
                .thenReturn(List.of(new RentalDetails(rental, "Drill", "bob")));
        when(rentalService.confirmReturn(5L, 1L)).thenThrow(new InputClosedException());

        String output = run("1\nalice\nsecret\n4\n1\n1\n");

        assertTrue(output.contains("Goodbye."));
    }

    // -------------------------------------------------------------------------
    // guarded
    // -------------------------------------------------------------------------

    @Test
    void guardedPrintsBusinessRuleMessage() {
        stubAliceLogin();
        when(itemService.listItem(eq(1L), anyString(), anyString(), any()))
                .thenThrow(new BusinessRuleException("Name is required"));

        String output = run("1\nalice\nsecret\n1\n\ndesc\n3.00\n5\n3\n");

        assertTrue(output.contains("Name is required"));
    }

    @Test
    void guardedLogsNotFoundAndShowsFriendlyMessage() {
        stubAliceLogin();
        when(itemService.getInventory(1L)).thenThrow(new NotFoundException("gone"));

        String output = run("1\nalice\nsecret\n2\n5\n3\n");

        assertTrue(output.contains("That record no longer exists."));
        verify(logger).warn(eq(1L), eq("item.inventory.not_found"), any(Map.class));
    }

    @Test
    void guardedLogsDatabaseError() {
        stubAliceLogin();
        when(itemService.getInventory(1L)).thenThrow(new DatabaseException("disk"));

        String output = run("1\nalice\nsecret\n2\n5\n3\n");

        assertTrue(output.contains("Something went wrong while accessing your data"));
        verify(logger).error(eq(1L), eq("item.inventory.database_error"),
                any(Map.class), any(DatabaseException.class));
    }

    @Test
    void guardedNotFoundWhileLoggedOutUsesNullUserId() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenThrow(new NotFoundException("gone"));

        String output = run("1\nalice\nsecret\n3\n");

        assertTrue(output.contains("That record no longer exists."));
        verify(logger).warn(isNull(), eq("auth.login.not_found"), any(Map.class));
    }

    @Test
    void guardedDatabaseErrorWhileLoggedOutUsesNullUserId() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenThrow(new DatabaseException("disk"));

        String output = run("1\nalice\nsecret\n3\n");

        assertTrue(output.contains("Something went wrong while accessing your data"));
        verify(logger).error(isNull(), eq("auth.login.database_error"),
                any(Map.class), any(DatabaseException.class));
    }

    @Test
    void guardedInputClosedUnwindsToGoodbye() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenThrow(new InputClosedException());

        String output = run("1\nalice\nsecret\n");

        assertTrue(output.contains("Goodbye."));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Username is taken (not available) → login may proceed to password. */
    private void stubExistingUser(String username) {
        when(authService.isUsernameAvailable(username)).thenReturn(false);
    }

    private void stubAliceLogin() {
        stubExistingUser("alice");
        when(authService.login("alice", "secret")).thenReturn(user(1L, "alice"));
    }

    private static User user(long id, String name) {
        return new User(id, name, "hash", NOW);
    }

    private static Item item(long id, long ownerId, String name, ItemStatus status) {
        return new Item(id, ownerId, name, "desc", new BigDecimal("5.00"), status, NOW);
    }

    private ConsoleIO io(String scriptedInput) {
        return new ConsoleIO(
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private String run(String scriptedInput) {
        ByteArrayInputStream in =
                new ByteArrayInputStream(scriptedInput.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleIO consoleIO = new ConsoleIO(in, new PrintStream(out, true, StandardCharsets.UTF_8));
        new Transport(authService, itemService, rentalService, consoleIO, logger).run();
        return out.toString(StandardCharsets.UTF_8);
    }
}