// src/main/java/com/skybooking/security/TokenManager.java

package com.skybooking.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * 🎫 Gestionnaire de tokens de sécurité
 */
public class TokenManager {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Générer un token de session
     */
    public static String generateSessionToken() {
        return UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
    }
    
    /**
     * Générer un token de réinitialisation de mot de passe
     */
    public static String generateResetToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Générer un ID unique
     */
    public static String generateUniqueId(String prefix) {
        return prefix + System.currentTimeMillis();
    }
    
    /**
     * Générer un ID de transaction
     */
    public static String generateTransactionId(String methodPrefix) {
        return methodPrefix + System.currentTimeMillis() + RANDOM.nextInt(1000);
    }
    
    /**
     * Masquer un numéro de carte
     */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}