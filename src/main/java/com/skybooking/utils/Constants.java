// src/main/java/com/skybooking/utils/Constants.java

package com.skybooking.utils;

/**
 * 🔧 Constantes globales de l'application
 */
public class Constants {
    
    // ==================== CONFIGURATION SERVEUR ====================
    public static final int REST_PORT = 8080;
    public static final String NAMING_SERVICE = "NameService";
    public static final String CORBA_SERVICE_NAME = "FlightBookingSystem";
    
    // ==================== BASE DE DONNÉES ====================
    public static final String DB_NAME = "skybooking_db";
    public static final String MONGODB_URI_ENV = "MONGODB_URI";
    public static final String DEFAULT_MONGODB_URI = "mongodb://localhost:27017";
    
    // ==================== COLLECTIONS MONGODB ====================
    public static final String COLLECTION_CUSTOMERS = "customers";
    public static final String COLLECTION_FLIGHTS = "flights";
    public static final String COLLECTION_SEATS = "seats";
    public static final String COLLECTION_RESERVATIONS = "reservations";
    public static final String COLLECTION_TICKETS = "tickets";
    public static final String COLLECTION_PAYMENTS = "payments";
    public static final String COLLECTION_INVOICES = "invoices";
    public static final String COLLECTION_PAYMENT_METHODS = "payment_methods";
    public static final String COLLECTION_FAVORITES = "favorites";
    public static final String COLLECTION_NEWSLETTERS = "newsletters";
    public static final String COLLECTION_CANCELLATIONS = "cancellations";
    public static final String COLLECTION_REFUNDS = "refunds";
    public static final String COLLECTION_ADMINS = "admins";
    
    // ==================== SÉCURITÉ ====================
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final long LOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    public static final long RESET_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    public static final long SESSION_DURATION_MS = 8 * 60 * 60 * 1000; // 8 heures
    public static final long RESET_TOKEN_DURATION_MS = 30 * 60 * 1000; // 30 minutes
    
    // ==================== CHIFFREMENT AES-256 ====================
    // Configuration pour AES-256 bits (clé de 32 octets)
    public static final String AES_ALGORITHM = "AES";
    public static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"; // Mode CBC pour plus de sécurité
    public static final int AES_KEY_SIZE = 256; // Bits
    public static final int AES_IV_SIZE = 16; // Octets (128 bits pour le vecteur d'initialisation)
    public static final String AES_KEY_ENV = "AES_SECRET_KEY";
    
    // ==================== POLITIQUE D'ANNULATION ====================
    public static final long HOURS_FREE_CANCELLATION = 48;
    public static final long HOURS_PARTIAL_REFUND = 24;
    public static final double FREE_CANCELLATION_REFUND = 1.0; // 100%
    public static final double PARTIAL_REFUND_PERCENTAGE = 0.5; // 50%
    public static final double LATE_CANCELLATION_FEE = 5000.0; // 5000 DZD
    
    // ==================== DESTINATIONS FAVORITES ====================
    public static final int MAX_FAVORITES_PER_USER = 20; // Limite par utilisateur
    public static final int POPULAR_DESTINATIONS_LIMIT = 6; // Nombre de destinations populaires à afficher

    
    // ==================== TIMEOUT ====================
    public static final long TIMEOUT_LOGIN = 10L;
    public static final long TIMEOUT_SEARCH = 15L;
    public static final long TIMEOUT_RESERVATION = 20L;
    public static final long TIMEOUT_PAYMENT = 15L;
    public static final long TIMEOUT_DEFAULT = 10L;
    public static final long TIMEOUT_ADMIN = 15L;
    public static final long TIMEOUT_CANCELLATION = 20L;
    public static final long TIMEOUT_MODIFICATION = 15L;
    
    // ==================== FICHIERS ====================
    public static final String TICKETS_DIR = "tickets";
    public static final String INVOICES_DIR = "invoices";
    public static final String TEMP_IMAGES_DIR = "temp_images";
    
    // ==================== VALIDATION ====================
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PHONE_REGEX = "^\\+?[0-9]{10,15}$";
    
    // ==================== PRIX & TVA ====================
    public static final double TAX_RATE = 0.19; // TVA 19%
    
    // ==================== MESSAGES ====================
    public static final String MSG_INVALID_CREDENTIALS = "Nom d'utilisateur ou mot de passe incorrect";
    public static final String MSG_USER_EXISTS = "Ce nom d'utilisateur existe déjà";
    public static final String MSG_FLIGHT_NOT_FOUND = "Vol non trouvé";
    public static final String MSG_SEAT_NOT_AVAILABLE = "Siège non disponible";
    public static final String MSG_RESERVATION_SUCCESS = "Réservation créée avec succès";
    
    private Constants() {
        // Empêcher l'instanciation
    }
}
