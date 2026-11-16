package com.marquee.model;

import com.google.gson.annotations.SerializedName;

public record Screening(
        @SerializedName("screening_id") Long screeningId,
        @SerializedName("film_id") Long filmId,
        @SerializedName("film_title") String filmTitle,
        String venue,
        @SerializedName("screening_time") String screeningTime) {
}