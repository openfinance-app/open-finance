package org.openfinance.entity;

import java.time.LocalDate;

/** Interest eligibility dates; never change the transaction's booking date. */
public enum InterestValueDateMode {
    TRANSACTION_DATE(0),
    DAYS_1(1),
    DAYS_2(2),
    DAYS_3(3),
    DAYS_4(4),
    DAYS_5(5),
    FORTNIGHT(0);

    private final int offset;

    InterestValueDateMode(int offset) {
        this.offset = offset;
    }

    public LocalDate valueDate(LocalDate booked, boolean credit) {
        if (this != FORTNIGHT) return booked.plusDays(credit ? offset : -offset);
        if (credit) {
            return booked.getDayOfMonth() <= 15
                    ? booked.withDayOfMonth(16)
                    : booked.withDayOfMonth(1).plusMonths(1);
        }
        return booked.withDayOfMonth(booked.getDayOfMonth() <= 15 ? 1 : 16);
    }

    public static InterestValueDateMode fromSkrooge(String code) {
        return switch (code) {
            case "F" -> FORTNIGHT;
            case "0", "" -> TRANSACTION_DATE;
            case "1" -> DAYS_1;
            case "2" -> DAYS_2;
            case "3" -> DAYS_3;
            case "4" -> DAYS_4;
            case "5" -> DAYS_5;
            default -> throw new IllegalArgumentException("Unsupported interest value-date mode");
        };
    }
}
