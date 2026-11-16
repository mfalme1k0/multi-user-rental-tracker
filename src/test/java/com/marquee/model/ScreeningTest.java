package com.marquee.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ScreeningTest {

    private static final Gson GSON = new Gson();

    private static Screening sample() {
        return new Screening(12L, 1L, "The Invisible Fight", "Main Hall", "2025-10-03T19:30:00Z");
    }

    private static JsonObject toJson(Screening screening) {
        return JsonParser.parseString(GSON.toJson(screening)).getAsJsonObject();
    }

    @Test
    void serializesToTheShapeInTheBrief() {
        JsonObject json = toJson(sample());

        assertEquals(12L, json.get("screening_id").getAsLong());
        assertEquals(1L, json.get("film_id").getAsLong());
        assertEquals("The Invisible Fight", json.get("film_title").getAsString());
        assertEquals("Main Hall", json.get("venue").getAsString());
        assertEquals("2025-10-03T19:30:00Z", json.get("screening_time").getAsString());
    }

    @Test
    void doesNotLeakCamelCaseFieldNames() {
        JsonObject json = toJson(sample());

        assertFalse(json.has("screeningId"));
        assertFalse(json.has("filmId"));
        assertFalse(json.has("filmTitle"));
        assertFalse(json.has("screeningTime"));
    }

    @Test
    void hasExactlyFiveJsonFields() {
        assertEquals(5, toJson(sample()).size());
    }

    @Test
    void unsavedScreeningOmitsServerFields() {
        Screening unsaved = new Screening(null, 1L, null, "Main Hall", "2025-10-03T19:30:00Z");
        JsonObject json = toJson(unsaved);

        assertFalse(json.has("screening_id"));
        assertFalse(json.has("film_title"));
        assertEquals(1L, json.get("film_id").getAsLong());
    }

    @Test
    void roundTripsThroughJson() {
        assertEquals(sample(), GSON.fromJson(GSON.toJson(sample()), Screening.class));
    }

    @Test
    void deserializesFromSnakeCaseJson() {
        String body = "{\"film_id\": 3, \"venue\": \"Rooftop\", \"screening_time\": \"2025-11-01T20:00:00Z\"}";

        Screening screening = GSON.fromJson(body, Screening.class);

        assertEquals(3L, screening.filmId());
        assertEquals("Rooftop", screening.venue());
        assertEquals("2025-11-01T20:00:00Z", screening.screeningTime());
    }

    @Test
    void equalityCoversAllFields() {
        Screening other = new Screening(12L, 1L, "The Invisible Fight", "Side Room", "2025-10-03T19:30:00Z");

        assertEquals(sample(), sample());
        assertNotEquals(sample(), other);
    }
}