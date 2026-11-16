package com.marquee.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilmTest {

    private static final Gson GSON = new Gson();
    private static final Genre ACTION = new Genre(2L, "Action");
    private static final Genre COMEDY = new Genre(5L, "Comedy");

    private static Film sample() {
        return new Film(1L, "The Invisible Fight", 2023,
                "A heavy metal fan in Soviet Estonia encounters monks who practice kung fu.",
                "2025-09-15T10:30:00Z", List.of(ACTION, COMEDY));
    }

    private static JsonObject toJson(Film film) {
        return JsonParser.parseString(GSON.toJson(film)).getAsJsonObject();
    }

    // ---- JSON shape ---------------------------------------------------------

    @Test
    void serializesWithSnakeCaseFieldNames() {
        JsonObject json = toJson(sample());

        assertEquals(1L, json.get("film_id").getAsLong());
        assertEquals("2025-09-15T10:30:00Z", json.get("created_at").getAsString());
        assertFalse(json.has("filmId"));
        assertFalse(json.has("createdAt"));
    }

    @Test
    void serializesNestedGenres() {
        JsonObject json = toJson(sample());

        assertEquals(2, json.getAsJsonArray("genres").size());
        JsonObject first = json.getAsJsonArray("genres").get(0).getAsJsonObject();
        assertEquals(2L, first.get("genre_id").getAsLong());
        assertEquals("Action", first.get("name").getAsString());
    }

    @Test
    void nullDescriptionIsOmittedFromJson() {
        Film film = new Film(1L, "Untitled", 2000, null, "2025-01-01T00:00:00Z", List.of());

        assertFalse(toJson(film).has("description"));
    }

    @Test
    void unsavedFilmOmitsServerGeneratedFields() {
        Film film = new Film(null, "New film", 2024, null, null, null);
        JsonObject json = toJson(film);

        assertFalse(json.has("film_id"));
        assertFalse(json.has("created_at"));
    }

    @Test
    void roundTripsThroughJson() {
        Film restored = GSON.fromJson(GSON.toJson(sample()), Film.class);

        assertEquals(sample(), restored);
    }

    // ---- withoutDescription (list view) ------------------------------------

    @Test
    void withoutDescriptionOmitsOnlyTheDescription() {
        JsonObject json = toJson(sample().withoutDescription());

        assertFalse(json.has("description"));
        assertEquals("The Invisible Fight", json.get("title").getAsString());
        assertEquals(2023, json.get("year").getAsInt());
        assertEquals(2, json.getAsJsonArray("genres").size());
    }

    @Test
    void withoutDescriptionDoesNotChangeTheOriginal() {
        Film original = sample();
        original.withoutDescription();

        assertNotNull(original.description());
    }

    // ---- genres handling ----------------------------------------------------

    @Test
    void nullGenresBecomeEmptyList() {
        Film film = new Film(1L, "t", 2000, null, null, null);

        assertNotNull(film.genres());
        assertTrue(film.genres().isEmpty());
        assertEquals(0, toJson(film).getAsJsonArray("genres").size());
    }

    @Test
    void genresAreCopiedDefensively() {
        List<Genre> source = new ArrayList<>(List.of(ACTION));
        Film film = new Film(1L, "t", 2000, null, null, source);

        source.add(COMEDY);

        assertEquals(1, film.genres().size());
    }

    @Test
    void genresListCannotBeModified() {
        Film film = sample();

        assertThrows(UnsupportedOperationException.class, () -> film.genres().add(COMEDY));
    }

    @Test
    void withGenresReturnsNewFilmAndLeavesOriginalUntouched() {
        Film base = new Film(1L, "t", 2000, "d", "2025-01-01T00:00:00Z", List.of());

        Film updated = base.withGenres(List.of(ACTION));

        assertEquals(0, base.genres().size());
        assertEquals(List.of(ACTION), updated.genres());
        assertEquals(base.filmId(), updated.filmId());
        assertEquals(base.title(), updated.title());
        assertEquals(base.description(), updated.description());
    }
}