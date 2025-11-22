// src/main/java/com/skybooking/utils/ValidationUtils.java

package com.skybooking.utils;

import java.util.regex.Pattern;

/**
 * ✅ Utilitaires de validation
 */
public class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(Constants.EMAIL_REGEX);
    private static final Pattern PHONE_PATTERN = Pattern.compile(Constants.PHONE_REGEX);
    
    /**
     * Valider un nom d'utilisateur
     */
    public static boolean isValidUsername(String username) {
        return username != null 
            && !username.trim().isEmpty() 
            && username.length() >= Constants.MIN_USERNAME_LENGTH;
    }
    
    /**
     * Valider un mot de passe
     */
    public static boolean isValidPassword(String password) {
        return password != null 
            && password.length() >= Constants.MIN_PASSWORD_LENGTH;
    }
    
    /**
     * Valider un email
     */
    public static boolean isValidEmail(String email) {
        return email != null 
            && !email.trim().isEmpty() 
            && EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Valider un numéro de téléphone
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String cleanPhone = phone.replaceAll("\\s+", "");
        return PHONE_PATTERN.matcher(cleanPhone).matches();
    }
    
    /**
     * Nettoyer un numéro de téléphone
     */
    public static String cleanPhoneNumber(String phone) {
        return phone.replaceAll("\\s+", "");
    }
    
    /**
     * Valider qu'une chaîne n'est pas vide
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * Valider un montant
     */
    public static boolean isValidAmount(double amount) {
        return amount > 0;
    }
    
    /**
     * Valider une date au format yyyy-MM-dd
     */
    public static boolean isValidDateFormat(String date) {
        if (date == null || date.isEmpty()) {
            return false;
        }
        try {
            DateUtils.parseDate(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Valider une carte bancaire (basique)
     */
    public static boolean isValidCardNumber(String cardNumber) {
        return cardNumber != null && cardNumber.replaceAll("\\s+", "").length() >= 16;
    }
    
    /**
     * Valider un CVV
     */
    public static boolean isValidCVV(String cvv) {
        return cvv != null && cvv.length() == 3 && cvv.matches("\\d{3}");
    }
    
    /**
     * Valider une date d'expiration (MM/YY)
     */
    public static boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null || !expiryDate.matches("\\d{2}/\\d{2}")) {
            return false;
        }
        
        try {
            String[] parts = expiryDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            
            if (month < 1 || month > 12) {
                return false;
            }
            
            // Vérifier si la carte n'est pas expirée
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int currentYear = cal.get(java.util.Calendar.YEAR) % 100;
            int currentMonth = cal.get(java.util.Calendar.MONTH) + 1;
            
            return year > currentYear || (year == currentYear && month >= currentMonth);
            
        } catch (Exception e) {
            return false;
        }
    }
}