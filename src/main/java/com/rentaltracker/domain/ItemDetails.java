package com.rentaltracker.domain;

/** Read model for the item detail screen: the item plus its owner's username. Assembled by ItemService. */
public record ItemDetails(Item item, String ownerUsername) {
}
