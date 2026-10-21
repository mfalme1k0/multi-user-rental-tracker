package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;

public class RentalValidatorImpl implements RentalValidator {

    private static final int MIN_RENTAL_DAYS = 1;

    @Override
    public int validateRentalDuration(int days) {
        if (days < MIN_RENTAL_DAYS) {
            throw new ValidationException(
                    "Rental duration must be at least " + MIN_RENTAL_DAYS + " day");
        }

        return days;
    }
}
