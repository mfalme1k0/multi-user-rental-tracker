package com.marquee.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class GenreTest {

    private static final Gson GSON = new Gson();

    private static JsonObject toJson(Genre genre) {
        return JsonParser.parseString(GSON.toJson(genre)).getAsJsonObject();
    }

    @Test
    void serializesWithSnakeCaseFieldNames() {
        JsonObject json = toJson(new Genre(5L, "Comedy"));

        assertEquals(5L, json.get("genre_id").getAsLong());
        assertEquals("Comedy", json.get("name").getAsString());
        assertFalse(json.has("genreId"));
    }

    @Test
    void unsavedGenreOmitsId() {
        JsonObject json = toJson(new Genre(null, "Horror"));

        assertFalse(json.has("genre_id"));
        assertEquals("Horror", json.get("name").getAsString());
    }

    @Test
    void roundTripsThroughJson() {
        Genre genre = new Genre(2L, "Action");

        assertEquals(genre, GSON.fromJson(GSON.toJson(genre), Genre.class));
    }

    @Test
    void deserializesFromSnakeCaseJson() {
        Genre genre = GSON.fromJson("{\"genre_id\": 7, \"name\": \"Drama\"}", Genre.class);

        assertEquals(7L, genre.genreId());
        assertEquals("Drama", genre.name());
    }

    @Test
    void equalityIsBasedOnIdAndName() {
        assertEquals(new Genre(1L, "Action"), new Genre(1L, "Action"));
        assertNotEquals(new Genre(1L, "Action"), new Genre(2L, "Action"));
        assertNotEquals(new Genre(1L, "Action"), new Genre(1L, "Comedy"));
    }
}