package com.rentaltracker;

import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.sqlite.SQLiteItemRepository;
import com.rentaltracker.repository.sqlite.SQLiteRentalRepository;
import com.rentaltracker.repository.sqlite.SQLiteTransactor;
import com.rentaltracker.repository.sqlite.SQLiteUserRepository;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.UserService;
import com.rentaltracker.transport.Transport;

import java.nio.file.Path;
import java.time.Clock;


public final class Main {

    // Database file used when nothing overrides it, relative to the working directory.
//    private static final String DEFAULT_DATABASE_FILE = "rental-tracker.db";

    private static final String DEFAULT_DATABASE_FILE =
            System.getenv().getOrDefault("DB_PATH", "rental-tracker.db");

    // System property that moves the database elsewhere , for testing or other purposes. If not set, the default is used.
    static final String DATABASE_PROPERTY = "rentaltracker.db";

    private Main() {}

    public static void main(String[] args) {
        System.out.println("Multi-User Rental Tracker");

        Path databasePath = Path.of(System.getProperty(DATABASE_PROPERTY, DEFAULT_DATABASE_FILE));

        DatabaseManager databaseManager = new DatabaseManager(databasePath);

        databaseManager.initialize();

        UserRepository userRepository = new SQLiteUserRepository(databaseManager);
        UserService userService = new UserService(userRepository);

        ItemRepository itemRepository = new SQLiteItemRepository(databaseManager);
        RentalRepository rentalRepository = new SQLiteRentalRepository(databaseManager);
        Transactor transactor = new SQLiteTransactor(databaseManager);

//        ItemService itemService = new ItemService(
//                itemRepository,
//                rentalRepository,
//                userRepository,
//                transactor);
//
//        Clock clock = Clock.systemDefaultZone();
//
//        RentalService rentalService = new RentalService(
//                itemRepository,
//                rentalRepository,
//                userService,
//                transactor,
//                clock
//        );
//
//        Transport transport = new Transport(
//                userService,
//                itemService,
//                rentalService
//        );
//
//        transport.start();
    }
}
