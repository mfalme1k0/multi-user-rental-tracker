package com.rentaltracker.validation;

import java.math.BigDecimal;

public interface ItemValidator {
    String validateName(String name);
    BigDecimal validateCostPerDay(BigDecimal costPerDay);
}
