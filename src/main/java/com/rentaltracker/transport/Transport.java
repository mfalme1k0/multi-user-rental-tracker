package com.rentaltracker.transport;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.BusinessRuleException;
import com.rentaltracker.service.UserService;

import com.rentaltracker.domain.*;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class Transport {

    private static final int PAGE_SIZE = 5;

    private final UserService userService;
    private final ItemService itemService;
    private final RentalService rentalService;
    private final Scanner input;

    public Transport(UserService userService, ItemService itemService, RentalService rentalService) {
        this.userService = userService;
        this.itemService = itemService;
        this.rentalService = rentalService;
        this.input = new Scanner(System.in);
    }

    //    Starts the application by determining whether this is the first launch
    //    If not, then starts the main menu for the owner already existing in DB.
    public void start() {
        Optional<User> owner = userService.findOwner();

        User currentOwner;

        if (owner.isPresent()) {
            currentOwner = subsequentRuns(owner.get());
        } else {
            currentOwner = firstLaunch();
        }

        runMenu(currentOwner);
    }

    //   Handles the first launch by asking for the owner's username, then registering them.
    public User firstLaunch() {
        System.out.print("Enter your username: ");
        String username = input.nextLine().trim().toLowerCase();

        User owner = userService.registerOwner(username);

        System.out.println("Welcome, " + owner.username() + "!");

        return owner;
    }

    //  Handles subsequent launches where user was already registered
    private User subsequentRuns(User user) {
        System.out.println("Welcome back, " + user.username() + "!");
        return user;
    }

    //    Displays the application's main menu options.
    private void printMenu() {
        System.out.println();
        System.out.println("=== Rental tracker ===");
        System.out.println("1) List an item");
        System.out.println("2) View my inventory");
        System.out.println("3) Record a rental");
        System.out.println("4) Confirm a return");
        System.out.println("5) Exit");
        System.out.print("> ");
    }

    //    Keeps the application running and routes each menu choice to the corresponding method operation.
    private void runMenu(User owner) {
        boolean running = true;

        while (running) {
            printMenu();

            String choice = input.nextLine().trim();

            switch (choice) {
                case "1" -> listItem(owner);
                case "2" -> viewInventory(owner);
                case "3" -> recordRental(owner);
                case "4" -> confirmReturn(owner);
                case "5" -> {
                    running = false;
                    System.out.println("Goodbye.");
                }

                default -> System.out.println("Invalid option.");
            }
        }
    }

    //    Collects item details from the owner and asks the service layer to create the listing.
    private void listItem(User owner) {
        System.out.println();
        System.out.println("=== List an item ===");

        System.out.print("Name: ");
        String name = input.nextLine().trim();

        System.out.print("Description: ");
        String description = input.nextLine().trim();

        System.out.print("Cost per day: ");

        BigDecimal costPerDay;

        try {
            costPerDay = new BigDecimal(input.nextLine().trim());

        } catch (NumberFormatException e) {
            System.out.println("Invalid cost.");
            return;
        }

        try {
            itemService.listItem(
                    owner.id(),
                    name,
                    description,
                    costPerDay
            );

            System.out.println("Item listed successfully.");

        } catch (BusinessRuleException e) {
            System.out.println(e.getMessage());
        }
    }

    //    Displays the owner's inventory with pagination and allows the owner to open an individual item's details.
    private void viewInventory(User owner) {
        List<Item> items = itemService.getInventory(owner.id());

        if (items.isEmpty()) {
            printEmptyInventory();
            return;
        }

        int page = 0;

        while (true) {
            int start = page * PAGE_SIZE;
            int end = Math.min(
                    start + PAGE_SIZE,
                    items.size()
            );

            printInventoryPage(items, start, end);

            String choice = readPaginationChoice(
                    page,
                    end,
                    items.size()
            );

            if (choice.equals("B")) {
                return;
            }

            if (choice.equals("N") && end < items.size()) {
                page++;
                continue;
            }

            if (choice.equals("P") && page > 0) {
                page--;
                continue;
            }

            Integer selected = parseSelection(choice);

            if (selected != null
                    && selected >= 1
                    && selected <= end - start) {

                Item item = items.get(start + selected - 1);

                showItemDetails(item, owner);

                items = itemService.getInventory(owner.id());

                continue;
            }

            System.out.println("Invalid option.");
        }
    }

    //    Displays the inventory's empty state when the owner has no items.
    private void printEmptyInventory() {
        System.out.println();
        System.out.println("=== My inventory ===");
        System.out.println("No items found.");
    }

    //    Displays one page of inventory items with their current status.
    private void printInventoryPage(
            List<Item> items,
            int start,
            int end) {

        System.out.println();
        System.out.println("=== My inventory ===");

        for (int i = start; i < end; i++) {
            Item item = items.get(i);

            System.out.printf(
                    "%d) %s - %s%n",
                    i - start + 1,
                    item.name(),
                    item.status()
                            .name()
                            .toLowerCase()
            );
        }
    }

    //    Displays an item's details and lets the owner delist or relist it.
    private void showItemDetails(Item item, User owner) {
        while (true) {
            printItemDetails(item, owner);

            System.out.println();

            if (item.status() == ItemStatus.AVAILABLE || item.status() == ItemStatus.RENTED) {
                System.out.println("1) Delist");
            } else {
                System.out.println("1) Relist");
            }

            System.out.println("2) Back to list");
            System.out.print("> ");

            String choice = input.nextLine().trim();

            if (choice.equals("2") || choice.equalsIgnoreCase("B")) {
                return;
            }

            if (!choice.equals("1")) {
                System.out.println("Invalid option.");
                continue;
            }

            item = changeItemStatus(item);
        }
    }

    //    Prints the detailed information for a single inventory item.
    private void printItemDetails(Item item, User owner) {
        System.out.println();
        System.out.println("=== " + item.name() + " ===");
        System.out.println("description    " + item.description());
        System.out.println("cost per day   " + item.costPerDay());
        System.out.println("status         " + item.status().name().toLowerCase());
        System.out.println("owner          " + owner.username());
        System.out.println("listing date   " + item.createdAt());
    }

    //    Requests the appropriate status change from the service layer and returns the updated item for display.
    private Item changeItemStatus(Item item) {
        try {
            if (item.status() == ItemStatus.AVAILABLE || item.status() == ItemStatus.RENTED) {
                Item delistedItem = itemService.delist(item.id());

                System.out.println("Item delisted.");
                return delistedItem;
            }

            // ItemStatus has no other value than UNLISTED left at this point.
            Item relistedItem = itemService.relist(item.id());

            System.out.println("Item relisted.");
            return relistedItem;

        } catch (BusinessRuleException e) {
            System.out.println(e.getMessage());
            return item;
        }
    }

    //    Displays the owner's available items with pagination and lets them select an item to start a rental.
    private void recordRental(User owner) {
        List<Item> items = itemService.getAvailableItems(owner.id());

        if (items.isEmpty()) {
            System.out.println();
            System.out.println("No available items.");
            return;
        }

        int page = 0;

        while (true) {
            int start = page * PAGE_SIZE;
            int end = Math.min(
                    start + PAGE_SIZE,
                    items.size()
            );

            printRentalItemPage(items, start, end);

            String choice = readPaginationChoice(
                    page,
                    end,
                    items.size()
            );

            if (choice.equals("B")) {
                return;
            }

            if (choice.equals("N") && end < items.size()) {
                page++;
                continue;
            }

            if (choice.equals("P") && page > 0) {
                page--;
                continue;
            }

            Integer selected = parseSelection(choice);

            if (selected != null
                    && selected >= 1
                    && selected <= end - start) {

                Item item = items.get(start + selected - 1);

                createRental(item);
                return;
            }

            System.out.println("Invalid option.");
        }
    }

    //   Displays one page of items that are currently available for rental.
    private void printRentalItemPage(
            List<Item> items,
            int start,
            int end) {

        System.out.println();
        System.out.println("=== Record a rental ===");

        for (int i = start; i < end; i++) {
            Item item = items.get(i);

            System.out.printf(
                    "%d) %s - %.2f/day%n",
                    i - start + 1,
                    item.name(),
                    item.costPerDay()
            );
        }
    }

    //    Collects renter's name and rental duration details, then asks the rental service to create the rental.
    private void createRental(Item item) {
        System.out.print("Renter username: ");
        String renterUsername = input.nextLine().trim();

        System.out.print("Duration in days: ");

        int durationDays;

        try {
            durationDays = Integer.parseInt(input.nextLine().trim());

        } catch (NumberFormatException e) {
            System.out.println("Invalid duration.");
            return;
        }

        try {
            Rental rental = rentalService.recordRental(
                    item.id(),
                    renterUsername,
                    durationDays
            );

            System.out.println();
            System.out.println("Rental recorded successfully.");
            System.out.println("Rental ID: " + rental.id());
            System.out.println("Ends: " + rental.endTime());

        } catch (BusinessRuleException e) {
            System.out.println(e.getMessage());
        }
    }

    //   Displays the owner's active rentals with pagination and lets them select a rental to view and confirm its return.
    private void confirmReturn(User owner) {

        List<RentalDetails> rentals = rentalService.getActiveRentals(owner.id());

        if (rentals.isEmpty()) {
            System.out.println();
            System.out.println("No active rentals.");
            return;
        }

        int page = 0;

        while (true) {
            int start = page * PAGE_SIZE;
            int end = Math.min(
                    start + PAGE_SIZE,
                    rentals.size()
            );

            printRentalReturnPage(rentals, start, end);

            String choice = readPaginationChoice(
                    page,
                    end,
                    rentals.size()
            );

            if (choice.equals("B")) {
                return;
            }

            if (choice.equals("N") && end < rentals.size()) {
                page++;
                continue;
            }

            if (choice.equals("P") && page > 0) {
                page--;
                continue;
            }

            Integer selected = parseSelection(choice);

            if (selected != null
                    && selected >= 1
                    && selected <= end - start) {

                RentalDetails rentalDetails = rentals.get(start + selected - 1);

                showRentalDetails(rentalDetails);

                // Refresh after a possible return so closed rentals disappear.
                rentals = rentalService.getActiveRentals(owner.id());

                if (rentals.isEmpty()) {
                    return;
                }

                if (page * PAGE_SIZE >= rentals.size()) {
                    page = Math.max(0, (rentals.size() - 1) / PAGE_SIZE);
                }

                continue;
            }

            System.out.println("Invalid option.");
        }
    }

    //    Displays one page of active rentals with the item and renter names.
    private void printRentalReturnPage(
            List<RentalDetails> rentals,
            int start,
            int end) {

        System.out.println();
        System.out.println("=== Confirm a return ===");

        for (int i = start; i < end; i++) {
            RentalDetails rentalDetails = rentals.get(i);

            System.out.printf(
                    "%d) %s - %s%n",
                    i - start + 1,
                    rentalDetails.itemName(),
                    rentalDetails.renterUsername()
            );
        }
    }

    //    Displays an active rental's details and allows the owner to confirm the return or go back to the rental list.
    private void showRentalDetails(RentalDetails rentalDetails) {
        Rental rental = rentalDetails.rental();

        while (true) {
            printRentalDetails(rentalDetails);

            System.out.println();

            // The list this screen is opened from only holds ACTIVE rentals.
            System.out.println("1) Confirm return");

            System.out.println("2) Back to list");
            System.out.print("> ");

            String choice = input.nextLine().trim();

            if (choice.equals("2") || choice.equalsIgnoreCase("B")) {
                return;
            }

            if (choice.equals("1")) {

                try {
                    rentalService.confirmReturn(rental.id());
                    System.out.println("Return confirmed successfully.");
                    return;

                } catch (BusinessRuleException e) {
                    System.out.println(e.getMessage());
                }

                continue;
            }

            System.out.println("Invalid option.");
        }
    }

    //    Prints the details of a rental selected from the active rental list.
    private void printRentalDetails(RentalDetails rentalDetails) {

        Rental rental = rentalDetails.rental();

        System.out.println();
        System.out.println("=== Rental " + rental.id() + " ===");
        System.out.println("item           " + rentalDetails.itemName());
        System.out.println("renter         " + rentalDetails.renterUsername());
        System.out.println("start time     " + rental.startTime());
        System.out.println("end time       " + rental.endTime());
        System.out.println("status         " + rental.status().name().toLowerCase());
    }

    //    Displays pagination options and reads the user's selection.
    private String readPaginationChoice(int page,
                                        int end,
                                        int totalItems) {

        printPaginationOptions(page, end, totalItems);

        return input.nextLine()
                .trim()
                .toUpperCase();
    }

    //    Displays only the pagination controls that are valid for the current page.
    private void printPaginationOptions(
            int page,
            int end,
            int totalItems) {

        System.out.println();
        System.out.println("B) Back to menu");

        if (page > 0) {
            System.out.println("P) Previous page");
        }

        if (end < totalItems) {
            System.out.println("N) Next page");
        }

        System.out.print("> ");
    }

    //    Converts a user's numeric menu selection into an Integer, returning null when the input is not a valid number.
    private Integer parseSelection(String choice) {
        try {
            return Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
            return null;
        }
    }
}
