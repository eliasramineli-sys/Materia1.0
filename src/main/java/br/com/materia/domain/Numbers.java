package br.com.materia.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Numbers {
    public static final long MAX_QUANTITY = 999_999_999_000L;
    public static final long MAX_MONEY = 99_999_999_999L;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private Numbers() {}

    public static long quantity(String value) { return parse(value, 3, MAX_QUANTITY, "Quantidade"); }
    public static long money(String value) { return parse(value, 2, MAX_MONEY, "Valor"); }

    private static long parse(String input, int scale, long max, String label) {
        String value = input == null ? "" : input.strip();
        // Sem separador de milhares: elimina a ambiguidade de 1.234.
        if (!value.matches("[0-9]+([.,][0-9]{1," + scale + "})?"))
            throw new IllegalArgumentException(label + ": use número positivo ou zero, sem separador de milhares (ex.: 1250,50).");
        try {
            long n = new BigDecimal(value.replace(',', '.')).movePointRight(scale).longValueExact();
            if (n > max) throw new ArithmeticException();
            return n;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException(label + " excede o limite permitido."); }
    }

    public static String qty(long value) {
        return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString().replace('.', ',');
    }
    public static String decimalMoney(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ','); }
    public static String currency(long cents) { return NumberFormat.getCurrencyInstance(BR).format(BigDecimal.valueOf(cents, 2)); }
    public static String currency(BigDecimal amount) { return NumberFormat.getCurrencyInstance(BR).format(amount); }
    public static BigDecimal valuation(long stock, long cost) {
        return BigDecimal.valueOf(stock, 3).multiply(BigDecimal.valueOf(cost, 2));
    }
    public static long weightedCost(long oldStock, long oldCost, long incoming, long newCost) {
        return BigDecimal.valueOf(oldStock).multiply(BigDecimal.valueOf(oldCost))
                .add(BigDecimal.valueOf(incoming).multiply(BigDecimal.valueOf(newCost)))
                .divide(BigDecimal.valueOf(oldStock + incoming), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
