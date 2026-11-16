package com.marquee.model;

import com.google.gson.annotations.SerializedName;

public record Genre(
        @SerializedName("genre_id") Long genreId,
        String name) {
}