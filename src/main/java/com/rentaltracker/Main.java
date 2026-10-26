package com.rentaltracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentaltracker.auth.AuthService;
import com.rentaltracker.auth.AuthServiceImpl;
import com.rentaltracker.auth.PasswordHasherImpl;
import com.rentaltracker.exception.DatabaseException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.logging.AppLogger;
import com.rentaltracker.logging.AppLoggerImpl;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.LogRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;
import com.rentaltracker.repository.sqlite.SQLiteItemRepository;
import com.rentaltracker.repository.sqlite.SQLiteLogRepository;
import com.rentaltracker.repository.sqlite.SQLiteRentalRepository;
import com.rentaltracker.repository.sqlite.SQLiteTransactor;
import com.rentaltracker.repository.sqlite.SQLiteUserRepository;
import com.rentaltracker.service.ItemService;
import com.rentaltracker.service.RentalService;
import com.rentaltracker.service.UserService;
import com.rentaltracker.transport.ConsoleIO;
import com.rentaltracker.transport.Transport;
import com.rentaltracker.validation.AuthValidatorImpl;
import com.rentaltracker.validation.ItemValidatorImpl;
import com.rentaltracker.validation.RentalValidatorImpl;

import java.nio.file.Path;
import java.time.Clock;


public final class Main {

    private static final String DEFAULT_DATABASE_FILE =
            System.getenv().getOrDefault("DB_PATH", "rental-tracker.db");

    // System property that moves the database elsewhere, for testing or other purposes.
    static final String DATABASE_PROPERTY = "rentaltracker.db";

    private Main() {}

    public static void main(String[] args) {
        System.out.println("Multi-User Rental Tracker");

        Path databasePath = Path.of(System.getProperty(DATABASE_PROPERTY, DEFAULT_DATABASE_FILE));

        DatabaseManager databaseManager = new DatabaseManager(databasePath);

        try {
            databaseManager.initialize();
        } catch (DatabaseException e) {
            // An unusable database is unrecoverable (D23).
            System.err.println("Could not open the database. See log for details.");
            System.exit(1);
            return;
        }

        UserRepository userRepository = new SQLiteUserRepository(databaseManager);
        ItemRepository itemRepository = new SQLiteItemRepository(databaseManager);
        RentalRepository rentalRepository = new SQLiteRentalRepository(databaseManager);
        LogRepository logRepository = new SQLiteLogRepository(databaseManager);
        Transactor transactor = new SQLiteTransactor(databaseManager);

        // Registers available Jackson modules, including Java Time support for LocalDateTime serialization.
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        AppLogger logger = new AppLoggerImpl(logRepository, new ObjectMapper());

        UserService userService = new UserService(userRepository);

        AuthService authService = new AuthServiceImpl(
                userRepository,
                new PasswordHasherImpl(),
                new AuthValidatorImpl(),
                transactor,
                logger);

        ItemService itemService = new ItemService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                new ItemValidatorImpl(),
                logger);

        RentalService rentalService = new RentalService(
                itemRepository,
                rentalRepository,
                userService,
                transactor,
                new RentalValidatorImpl(),
                Clock.systemUTC(),
                logger);

        new Transport(authService, itemService, rentalService, ConsoleIO.system(), logger).run();
    }
}