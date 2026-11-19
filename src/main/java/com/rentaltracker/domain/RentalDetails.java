package com.rentaltracker.domain;

/**
 * Read model for the "confirm a return" list and detail: a rental plus the item name and renter username.
 * Produced by ONE joined query in the repository so the list never issues a query per row.
 */
public record RentalDetails(Rental rental, String itemName, String renterUsername) {
}
