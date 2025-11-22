// src/main/java/com/skybooking/database/MongoDBConnector.java

package com.skybooking.database;

import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.skybooking.utils.Constants;
import org.bson.Document;

/**
 * 🔌 CONNECTEUR MONGODB - SINGLETON
 * Gestion de la connexion et de la base de données
 * 
 * Améliorations :
 * - Index pour les nouvelles collections (sessions, reset_tokens)
 * - Index TTL pour expiration automatique
 * - Statistiques détaillées améliorées
 * - Gestion d'erreurs renforcée
 */
public class MongoDBConnector {
    
    private static MongoDBConnector instance;
    private final MongoClient mongoClient;
    private final MongoDatabase database;
    
    private MongoDBConnector() {
        try {
            String connectionString = System.getenv(Constants.MONGODB_URI_ENV);
            if (connectionString == null || connectionString.isEmpty()) {
                connectionString = Constants.DEFAULT_MONGODB_URI;
            }
            
            System.out.println("→ Connexion à MongoDB : " + connectionString);
            this.mongoClient = MongoClients.create(connectionString);
            this.database = mongoClient.getDatabase(Constants.DB_NAME);
            
            createIndexes();
            
            System.out.println("✅ Connexion MongoDB établie avec succès");
            System.out.println("✅ Base de données : " + Constants.DB_NAME);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur connexion MongoDB : " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible de se connecter à MongoDB", e);
        }
    }
    
    public static synchronized MongoDBConnector getInstance() {
        if (instance == null) {
            instance = new MongoDBConnector();
        }
        return instance;
    }
    
    public MongoDatabase getDatabase() {
        return database;
    }
    
    private void createIndexes() {
    System.out.println("→ Création des index MongoDB avec support AES-256...");
    
    // ==================== CLIENTS ====================
    database.getCollection(Constants.COLLECTION_CUSTOMERS).createIndex(
        Indexes.ascending("username"), 
        new IndexOptions().unique(true)
    );
    database.getCollection(Constants.COLLECTION_CUSTOMERS).createIndex(
        Indexes.ascending("customerId")
    );
    database.getCollection(Constants.COLLECTION_CUSTOMERS).createIndex(
        Indexes.ascending("email")
    );
    
    // ==================== VOLS ====================
    database.getCollection(Constants.COLLECTION_FLIGHTS).createIndex(
        Indexes.ascending("flightId")
    );
    database.getCollection(Constants.COLLECTION_FLIGHTS).createIndex(
        Indexes.ascending("departureCity", "arrivalCity")
    );
    database.getCollection(Constants.COLLECTION_FLIGHTS).createIndex(
        Indexes.ascending("departureDate")
    );
    database.getCollection(Constants.COLLECTION_FLIGHTS).createIndex(
        Indexes.ascending("flightNumber", "departureDate")
    );
    database.getCollection(Constants.COLLECTION_FLIGHTS).createIndex(
        Indexes.ascending("availableSeats")
    );
    
    // ==================== SIÈGES ====================
    database.getCollection(Constants.COLLECTION_SEATS).createIndex(
        Indexes.ascending("flightId", "seatNumber")
    );
    database.getCollection(Constants.COLLECTION_SEATS).createIndex(
        Indexes.ascending("status")
    );
    database.getCollection(Constants.COLLECTION_SEATS).createIndex(
        Indexes.ascending("flightId", "status")
    );
    database.getCollection(Constants.COLLECTION_SEATS).createIndex(
        Indexes.ascending("seatClass")
    );
    
    // ==================== RÉSERVATIONS ====================
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("reservationId")
    );
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("customerId")
    );
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("flightId")
    );
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("status")
    );
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("customerId", "status")
    );
    // 🔒 Index pour l'algorithme de chiffrement (audit)
    database.getCollection(Constants.COLLECTION_RESERVATIONS).createIndex(
        Indexes.ascending("encryptionAlgorithm")
    );
    
    // ==================== TICKETS ====================
    database.getCollection(Constants.COLLECTION_TICKETS).createIndex(
        Indexes.ascending("reservationId")
    );
    database.getCollection(Constants.COLLECTION_TICKETS).createIndex(
        Indexes.ascending("ticketId")
    );
    // 🔒 Index pour le passeport masqué (recherche affichage)
    database.getCollection(Constants.COLLECTION_TICKETS).createIndex(
        Indexes.ascending("passengerDetails.passportNumberMasked")
    );
    
    // ==================== PAIEMENTS ====================
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("paymentId")
    );
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("reservationId")
    );
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("customerId")
    );
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("status")
    );
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("paymentDate")
    );
    // 🔒 Index pour la carte masquée (recherche affichage)
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("cardNumberMasked")
    );
    // 🔒 Index pour l'algorithme de chiffrement (audit sécurité)
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("encryptionAlgorithm")
    );
    // 🔒 Index pour la référence bancaire (recherche remboursements)
    database.getCollection(Constants.COLLECTION_PAYMENTS).createIndex(
        Indexes.ascending("bankReference")
    );
    
    // ==================== FACTURES ====================
    database.getCollection(Constants.COLLECTION_INVOICES).createIndex(
        Indexes.ascending("invoiceId")
    );
    database.getCollection(Constants.COLLECTION_INVOICES).createIndex(
        Indexes.ascending("paymentId")
    );
    
    // ==================== MÉTHODES DE PAIEMENT ====================
    database.getCollection(Constants.COLLECTION_PAYMENT_METHODS).createIndex(
        Indexes.ascending("customerId")
    );
    database.getCollection(Constants.COLLECTION_PAYMENT_METHODS).createIndex(
        Indexes.ascending("paymentMethodId")
    );
    database.getCollection(Constants.COLLECTION_PAYMENT_METHODS).createIndex(
        Indexes.ascending("customerId", "isDefault")
    );
    
    // ==================== FAVORIS ====================
    System.out.println("   → Création index FAVORITES...");
    
    // Index pour recherches par client (requête principale)
    database.getCollection(Constants.COLLECTION_FAVORITES).createIndex(
        Indexes.ascending("customerId")
    );

   // Index pour recherche par ville (statistiques)
    database.getCollection(Constants.COLLECTION_FAVORITES).createIndex(
        Indexes.ascending("cityName")
    );

    // Index composite unique pour éviter doublons
    database.getCollection(Constants.COLLECTION_FAVORITES).createIndex(
        Indexes.ascending("customerId", "cityName"),
    new IndexOptions().unique(true)
    );

    // Index pour tri chronologique
    database.getCollection(Constants.COLLECTION_FAVORITES).createIndex(
        Indexes.descending("addedAt")
    );

    System.out.println("   ✅ Index FAVORITES créés (4 index)");
    
    // ==================== NEWSLETTERS ====================
    database.getCollection(Constants.COLLECTION_NEWSLETTERS).createIndex(
        Indexes.ascending("email"),
        new IndexOptions().unique(true)
    );
    
    // ==================== ANNULATIONS ====================
    database.getCollection(Constants.COLLECTION_CANCELLATIONS).createIndex(
        Indexes.ascending("reservationId")
    );
    database.getCollection(Constants.COLLECTION_CANCELLATIONS).createIndex(
        Indexes.ascending("customerId")
    );
    
    // ==================== REMBOURSEMENTS ====================
    database.getCollection(Constants.COLLECTION_REFUNDS).createIndex(
        Indexes.ascending("reservationId")
    );
    database.getCollection(Constants.COLLECTION_REFUNDS).createIndex(
        Indexes.ascending("status")
    );
    
    // ==================== ADMINS ====================
    database.getCollection(Constants.COLLECTION_ADMINS).createIndex(
        Indexes.ascending("username"), 
        new IndexOptions().unique(true)
    );
    database.getCollection(Constants.COLLECTION_ADMINS).createIndex(
        Indexes.ascending("adminId")
    );
    
    // ==================== SESSIONS ====================
    database.getCollection("sessions").createIndex(
        Indexes.ascending("userId"),
        new IndexOptions().unique(true)
    );
    database.getCollection("sessions").createIndex(
        Indexes.ascending("token")
    );
    // Index TTL pour expiration automatique
    database.getCollection("sessions").createIndex(
        Indexes.ascending("expireAt"),
        new IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)
    );
    
    // ==================== RESET TOKENS ====================
    database.getCollection("password_reset_tokens").createIndex(
        Indexes.ascending("token"),
        new IndexOptions().unique(true)
    );
    database.getCollection("password_reset_tokens").createIndex(
        Indexes.ascending("customerId")
    );
    database.getCollection("password_reset_tokens").createIndex(
        Indexes.ascending("used")
    );
    // Index TTL pour expiration automatique
    database.getCollection("password_reset_tokens").createIndex(
        Indexes.ascending("expireAt"),
        new IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS)
    );
    
    System.out.println("✅ Index MongoDB créés avec succès");
    System.out.println("   • Collections standards : 13");
    System.out.println("   • Nouvelles collections : 2 (sessions, reset_tokens)");
    System.out.println("   • Index TTL : 2 (expiration automatique)");
    System.out.println("   🔒 Index sécurité AES-256 : 4 (champs chiffrés)");
    System.out.println("   ⭐ Index FAVORITES optimisés : 4");
}
    
    public void printDatabaseStats() {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║         STATISTIQUES MONGODB DÉTAILLÉES            ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        // Collections principales
        System.out.println("║ 👥 Clients        : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_CUSTOMERS).countDocuments()) + "║");
        System.out.println("║ ✈️  Vols          : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_FLIGHTS).countDocuments()) + "║");
        System.out.println("║ 💺 Sièges        : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_SEATS).countDocuments()) + "║");
        System.out.println("║ 📋 Réservations  : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_RESERVATIONS).countDocuments()) + "║");
        System.out.println("║ 🎫 Tickets       : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_TICKETS).countDocuments()) + "║");
        
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        // Paiements et finances
        System.out.println("║ 💳 Paiements     : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_PAYMENTS).countDocuments()) + "║");
        System.out.println("║ 🧾 Factures      : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_INVOICES).countDocuments()) + "║");
        System.out.println("║ 💰 Remboursements: " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_REFUNDS).countDocuments()) + "║");
        System.out.println("║ 💳 Méthodes paie.: " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_PAYMENT_METHODS).countDocuments()) + "║");
        
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        // Autres collections
        System.out.println("║ ❌ Annulations   : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_CANCELLATIONS).countDocuments()) + "║");
        System.out.println("║ ⭐ Favoris       : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_FAVORITES).countDocuments()) + "║");
        System.out.println("║ 📧 Newsletters   : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_NEWSLETTERS).countDocuments()) + "║");
        System.out.println("║ 👨‍💼 Admins       : " + 
            String.format("%-32s", database.getCollection(Constants.COLLECTION_ADMINS).countDocuments()) + "║");
        
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║           🆕 NOUVELLES COLLECTIONS                  ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        // Nouvelles collections avec TTL
        long activeSessions = database.getCollection("sessions").countDocuments();
        long totalResetTokens = database.getCollection("password_reset_tokens").countDocuments();
        long activeResetTokens = database.getCollection("password_reset_tokens").countDocuments(
            new Document("used", false)
        );
        
        System.out.println("║ 🔒 Sessions actives     : " + 
            String.format("%-23s", activeSessions) + "║");
        System.out.println("║ 🔑 Tokens reset (total) : " + 
            String.format("%-23s", totalResetTokens) + "║");
        System.out.println("║ 🔑 Tokens reset (actifs): " + 
            String.format("%-23s", activeResetTokens) + "║");
        
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║              📊 STATISTIQUES AVANCÉES              ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        
        // Statistiques des réservations
        long confirmedReservations = database.getCollection(Constants.COLLECTION_RESERVATIONS)
            .countDocuments(new Document("status", "CONFIRMED"));
        long cancelledReservations = database.getCollection(Constants.COLLECTION_RESERVATIONS)
            .countDocuments(new Document("status", "CANCELLED"));
        
        System.out.println("║ Réservations confirmées : " + 
            String.format("%-27s", confirmedReservations) + "║");
        System.out.println("║ Réservations annulées   : " + 
            String.format("%-27s", cancelledReservations) + "║");
        
        // Statistiques des sièges
        long availableSeats = database.getCollection(Constants.COLLECTION_SEATS)
            .countDocuments(new Document("status", "AVAILABLE"));
        long occupiedSeats = database.getCollection(Constants.COLLECTION_SEATS)
            .countDocuments(new Document("status", "OCCUPIED"));
        
        System.out.println("║ Sièges disponibles      : " + 
            String.format("%-27s", availableSeats) + "║");
        System.out.println("║ Sièges occupés          : " + 
            String.format("%-27s", occupiedSeats) + "║");
        
        // Taux d'occupation global
        long totalSeats = database.getCollection(Constants.COLLECTION_SEATS).countDocuments();
        double occupancyRate = totalSeats > 0 ? (double) occupiedSeats / totalSeats * 100 : 0;
        System.out.println("║ Taux d'occupation       : " + 
            String.format("%-20s", String.format("%.1f%%", occupancyRate)) + "║");
        
        System.out.println("╚════════════════════════════════════════════════════╝\n");
    }
    
    /**
     * Vérifier la santé de la connexion
     */
    public boolean checkHealth() {
        try {
            database.runCommand(new Document("ping", 1));
            System.out.println("✅ MongoDB Health Check: OK");
            return true;
        } catch (Exception e) {
            System.err.println("❌ MongoDB Health Check: FAILED - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Obtenir les informations du serveur
     */
    public void printServerInfo() {
        try {
            Document serverStatus = database.runCommand(new Document("serverStatus", 1));
            Document buildInfo = database.runCommand(new Document("buildInfo", 1));
            
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║           INFORMATIONS SERVEUR MONGODB             ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║ Version   : " + 
                String.format("%-40s", buildInfo.getString("version")) + "║");
            System.out.println("│ Uptime    : " + 
                String.format("%-40s", 
                    (serverStatus.get("uptime") != null ? serverStatus.get("uptime").toString() : "N/A") + " secondes") + "│");
           System.out.println("│ Host      : " + 
                String.format("%-40s", 
                    (serverStatus.get("host") != null ? serverStatus.get("host").toString() : "N/A")) + "│");
            System.out.println("╚════════════════════════════════════════════════════╝\n");
            
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de récupérer les infos serveur: " + e.getMessage());
        }
    }
    
    /**
     * Nettoyer les données expirées manuellement
     */
    public void cleanupExpiredData() {
        System.out.println("→ Nettoyage manuel des données expirées...");
        
        // Les tokens et sessions avec TTL sont nettoyés automatiquement par MongoDB
        // Cette méthode est un backup manuel si nécessaire
        
        long now = System.currentTimeMillis();
        
        // Nettoyer les sessions expirées
        long deletedSessions = database.getCollection("sessions")
            .deleteMany(new Document("expiresAt", new Document("$lt", now)))
            .getDeletedCount();
        
        // Nettoyer les tokens expirés
        long deletedTokens = database.getCollection("password_reset_tokens")
            .deleteMany(new Document("expiresAt", new Document("$lt", now)))
            .getDeletedCount();
        
        System.out.println("✅ Nettoyage terminé:");
        System.out.println("   • Sessions supprimées: " + deletedSessions);
        System.out.println("   • Tokens supprimés: " + deletedTokens);
    }
    
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("✅ Connexion MongoDB fermée");
        }
    }
}