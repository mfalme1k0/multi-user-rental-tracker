package com.marquee.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record Film(
        @SerializedName("film_id") Long filmId,
        String title,
        Integer year,
        String description,
        @SerializedName("created_at") String createdAt,
        List<Genre> genres) {

    /** Defensive copy so the list can never be mutated from outside. */
    public Film {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    /** Same film with its genres attached (repository loads genres separately). */
    public Film withGenres(List<Genre> newGenres) {
        return new Film(filmId, title, year, description, createdAt, newGenres);
    }

    /** List-view shape: description is omitted from the JSON. */
    public Film withoutDescription() {
        return new Film(filmId, title, year, null, createdAt, genres);
    }
}