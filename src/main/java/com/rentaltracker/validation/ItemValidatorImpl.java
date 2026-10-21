package com.rentaltracker.validation;

import com.rentaltracker.exception.ValidationException;

import java.math.BigDecimal;

public class ItemValidatorImpl implements ItemValidator {

    @Override
    public String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name must not be blank");
        }

        return name.strip();
    }

    @Override
    public BigDecimal validateCostPerDay(BigDecimal costPerDay) {
        if (costPerDay == null || costPerDay.signum() <= 0) {
            throw new ValidationException("costPerDay must be a positive amount");
        }

        return costPerDay;
    }
}
