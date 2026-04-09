package com.auction.util;

public class ValidationUtil {
    public static boolean isValidUsername(String username) {
        return username != null && !username.isBlank() && username.length() >= 3 && username.length() <= 50;
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public static boolean isValidPrice(double price) {
        return price > 0;
    }

    public static boolean isValidAmount(double amount) {
        return amount > 0;
    }
}
