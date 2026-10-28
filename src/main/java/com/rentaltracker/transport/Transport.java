package com.rentaltracker.transport;

import com.rentaltracker.auth.AuthService;
import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemDetails;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Page;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.exception.InputClosedException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.lang.System.out;


/**
 * CLI entry: menus, input collection, and result display.
 * Business rules and ownership live in the service layer; this class only orchestrates screens.
 */
public final class Transport {

    // Timestamps are stored as UTC (D9), so say so when displaying them.
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    private final AuthService authService;
    private final ItemService itemService;
    private final RentalService rentalService;
    private final ConsoleIO io;
    private final AppLogger logger;
    private final Session session;
    private final ListScreen listScreen;

    public Transport(AuthService authService,
                     ItemService itemService,
                     RentalService rentalService,
                     ConsoleIO io,
                     AppLogger logger) {
        this(authService, itemService, rentalService, io, logger, new Session());
    }

    // Package-private so tests can inspect or pre-seed the session.
    Transport(AuthService authService,
              ItemService itemService,
              RentalService rentalService,
              ConsoleIO io,
              AppLogger logger,
              Session session) {
        this.authService = Objects.requireNonNull(authService);
        this.itemService = Objects.requireNonNull(itemService);
        this.rentalService = Objects.requireNonNull(rentalService);
        this.io = Objects.requireNonNull(io);
        this.logger = Objects.requireNonNull(logger);
        this.session = Objects.requireNonNull(session);
        this.listScreen = new ListScreen(io);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Runs until the user exits or input ends. Always logs out on the way out. */
    public void run() {
        try {
            boolean running = true;
            while (running) {
                running = session.isLoggedIn() ? mainMenu() : authMenu();
            }
        } catch (InputClosedException e) {
            io.println();
        } finally {
            session.logOut();
            io.println("Goodbye.");
        }
    }

    // --------------------------------------------------- authentication

    private boolean authMenu() {
        io.println();
        io.println("=== Rental Tracker ===");
        io.println("1. Log in");
        io.println("2. Register");
        io.println("3. Exit");

        switch (io.readLine("> ")) {
            case "1" -> guarded("auth.login", this::logIn);
            case "2" -> guarded("auth.register", this::register);
            case "3" -> {
                return false;
            }
            default -> io.println("Invalid option.");
        }
        return true;
    }

    private void logIn() {
        String username = io.readLine("Username: ");
        if(authService.isUsernameAvailable(username)){
            io.println("Please create an account first.");
            return;
        }

        String password = io.readPassword("Password: ");

        User user = authService.login(username, password);
        session.logIn(user);
        io.println("Welcome back, " + user.username() + "!");
    }

    private void register() {
        String username = io.readLine("Choose a username: ");
        if(!authService.isUsernameAvailable(username)){
            io.println("invalid username");
            return;}
        String password = io.readPassword("Choose a password: ");
        String confirmation = io.readPassword("Confirm password: ");

        if (!password.equals(confirmation)) {
            io.println("Passwords do not match.");
            return;
        }

        User user = authService.register(username, password);
        session.logIn(user);
        io.println("Account created. Welcome, " + user.username() + "!");
    }

    // -------------------------------------------------------- main menu

    private boolean mainMenu() {
        io.println();
        io.println("=== Rental Tracker ===");
        io.println("Logged in as " + session.currentUser().username());
        io.println("1. List an item");
        io.println("2. View my inventory");
        io.println("3. Browse listings");
        io.println("4. Confirm a return");
        io.println("5. Log out");

        switch (io.readLine("> ")) {
            case "1" -> guarded("item.list", this::listItem);
            case "2" -> guarded("item.inventory", this::viewInventory);
            case "3" -> guarded("item.browse", this::browseListings);
            case "4" -> guarded("rental.confirm_menu", this::confirmReturn);
            case "5" -> {
                session.logOut();
                return authMenu();
            }
            default -> io.println("Invalid option.");
        }
        return true;
    }

    // --------------------------------------------------- list an item

    private void listItem() {
        io.println();
        io.println("=== List an item ===");

        String name = io.readLine("Name: ");
        String description = io.readLine("Description: ");

        Optional<BigDecimal> cost = readDecimal("Cost per day: ");
        if (cost.isEmpty()) {
            io.println("Invalid cost.");
            return;
        }

        // Blank name, non-positive cost and so on are rejected by ItemService (ValidationException).
        Item item = itemService.listItem(session.userId(), name, description, cost.get());
        io.println("Item listed successfully (id " + item.id() + ").");
    }

    // ----------------------------------------------------- inventory

    private void viewInventory() {
        while (true) {
            List<Item> items = itemService.getInventory(session.userId());
            if (items.isEmpty()) {
                io.println();
                io.println("No items found.");
                return;
            }

            Optional<Item> selected = listScreen.select(
                    "My inventory",
                    items,
                    item -> item.name() + " - " + statusText(item.status()));
            if (selected.isEmpty()) {
                return;
            }
            showOwnItem(selected.get().id());
        }
    }

    private void showOwnItem(long itemId) {
        while (true) {
            ItemDetails details = itemService.getDetails(itemId);
            Item item = details.item();

            printItemDetails(details);

            boolean unlisted = item.status() == ItemStatus.UNLISTED;
            io.println();
            io.println(unlisted ? "1) Relist" : "1) Delist");
            io.println("2) Back to list");

            String choice = io.readLine("> ");
            if (choice.equals("2") || choice.equalsIgnoreCase("B")) {
                return;
            }
            if (!choice.equals("1")) {
                io.println("Invalid option.");
                continue;
            }

            // Ownership and the relist guard are enforced by the service; show its message and stay on screen.
            guarded(unlisted ? "item.relist" : "item.delist", () -> {
                if (unlisted) {
                    itemService.relist(itemId, session.userId());
                    io.println("Item relisted.");
                } else {
                    itemService.delist(itemId, session.userId());
                    io.println("Item delisted.");
                }
            });
        }
    }

    // ----------------------------------------------------- browse and rent

    private void browseListings() {
        while (true) {
            Optional<Item> selected = listScreen.select(
                    "Browse listings",
                    pageNumber -> {
                        Page<Item> page = itemService.getMarketplaceListings(
                                session.userId(), pageNumber, ListScreen.PAGE_SIZE);
                        return new ListScreen.PageView<>(page.items(), page.hasNext());
                    },
                    item -> item.name() + " - " + item.costPerDay().toPlainString() + "/day");

            if (selected.isEmpty()) {
                // Distinguish "no listings at all" from user choosing Back on a non-empty list.
                Page<Item> first = itemService.getMarketplaceListings(
                        session.userId(), 0, ListScreen.PAGE_SIZE);
                if (first.items().isEmpty()) {
                    io.println();
                    io.println("No listings from other users are available.");
                }
                return;
            }
            showListing(selected.get().id());
        }
    }

    private void showListing(long itemId) {
        ItemDetails details = itemService.getDetails(itemId);
        printItemDetails(details);

        io.println();
        io.println("1) Rent this item");
        io.println("2) Back to list");

        String choice = io.readLine("> ");
        if (choice.equals("1")) {
            rent(itemId);
        } else if (!choice.equals("2") && !choice.equalsIgnoreCase("B")) {
            io.println("Invalid option.");
        }
    }

    private void rent(long itemId) {
        Optional<Integer> days = readInt("Duration in days: ");
        if (days.isEmpty()) {
            io.println("Invalid duration.");
            return;
        }

        // The renter is the logged-in user. Self-rental, availability and duration rules live in RentalService.
        Rental rental = rentalService.recordRental(itemId, session.userId(), days.get());
        io.println();
        io.println("Rental recorded successfully.");
        io.println("Rental ID: " + rental.id());
        io.println("Ends: " + formatTime(rental.endTime()));
    }

    // ---------------------------------------------------- confirm a return

    private void confirmReturn() {
        while (true) {
            List<RentalDetails> rentals = rentalService.getActiveRentals(session.userId());
            if (rentals.isEmpty()) {
                io.println();
                io.println("No active rentals.");
                return;
            }

            Optional<RentalDetails> selected = listScreen.select(
                    "Confirm a return",
                    rentals,
                    r -> r.itemName() + " - " + r.renterUsername()
                            + " (due " + formatTime(r.rental().endTime()) + ")");
            if (selected.isEmpty()) {
                return;
            }
            tryConfirmReturn(selected.get());
        }
    }

    /** @return true when the rental was closed so the caller can refresh the list */
    private boolean tryConfirmReturn(RentalDetails details) {
        Rental rental = details.rental();

        while (true) {
            io.println();
            io.println("=== Rental " + rental.id() + " ===");
            io.println("item           " + details.itemName());
            io.println("renter         " + details.renterUsername());
            io.println("start time     " + formatTime(rental.startTime()));
            io.println("end time       " + formatTime(rental.endTime()));
            io.println("status         " + statusText(rental.status()));

            io.println();
            io.println("1) Confirm return");
            io.println("2) Back to list");

            String choice = io.readLine("> ");
            if (choice.equals("2") || choice.equalsIgnoreCase("B")) {
                return false;
            }
            if (!choice.equals("1")) {
                io.println("Invalid option.");
                continue;
            }

            try {
                rentalService.confirmReturn(rental.id(), session.userId());
                io.println("Return confirmed successfully.");
                return true;
            } catch (InputClosedException e) {
                throw e;
            } catch (BusinessRuleException e) {
                io.println(e.getMessage());
            } catch (NotFoundException e) {
                logger.warn(session.userId(), "rental.confirm.not_found",
                        Map.of("message", nullToDash(e.getMessage())));
                io.println("That record no longer exists.");
            } catch (DatabaseException e) {
                logger.error(session.userId(), "rental.confirm.database_error",
                        Map.of("message", nullToDash(e.getMessage())), e);
                io.println("Something went wrong while accessing your data. Please try again.");
            }
        }
    }

    // ---------------------------------------------------- shared helpers

    private void printItemDetails(ItemDetails details) {
        Item item = details.item();
        io.println();
        io.println("=== " + item.name() + " ===");
        io.println("description    " + nullToDash(item.description()));
        io.println("cost per day   " + item.costPerDay().toPlainString());
        io.println("status         " + statusText(item.status()));
        io.println("owner          " + details.ownerUsername());
        io.println("listed         " + formatTime(item.createdAt()));
    }

    private void guarded(String operation, Runnable action) {
        try {
            action.run();
        } catch (InputClosedException e) {
            throw e; // not an error: unwinds to run()
        } catch (BusinessRuleException e) {
            io.println(e.getMessage());
        } catch (NotFoundException e) {
            Long userId = session.isLoggedIn() ? session.userId() : null;
            logger.warn(userId, operation + ".not_found", Map.of(
                    "message", nullToDash(e.getMessage())));
            io.println("That record no longer exists.");
        } catch (DatabaseException e) {
            Long userId = session.isLoggedIn() ? session.userId() : null;
            logger.error(userId, operation + ".database_error", Map.of(
                    "message", nullToDash(e.getMessage())), e);
            io.println("Something went wrong while accessing your data. Please try again.");
        }
    }

    private Optional<Integer> readInt(String prompt) {
        try {
            return Optional.of(Integer.parseInt(io.readLine(prompt)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> readDecimal(String prompt) {
        try {
            return Optional.of(new BigDecimal(io.readLine(prompt)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String statusText(Enum<?> status) {
        return status.name().toLowerCase();
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "-" : DISPLAY_TIME.format(time);
    }

    private static String nullToDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }
}