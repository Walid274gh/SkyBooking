// src/main/java/com/skybooking/security/SessionManager.java

package com.skybooking.security;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.skybooking.database.MongoDBConnector;
import com.skybooking.utils.Constants;
import org.bson.Document;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 🔒 Gestionnaire de sessions utilisateur avec persistance MongoDB
 * Améliorations :
 * - Persistance des sessions dans MongoDB
 * - Expiration automatique avec TTL index
 * - Support du clustering et load balancing
 * - Nettoyage automatique des sessions expirées
 */
public class SessionManager {
    
    private final MongoCollection<Document> sessionsCollection;
    
    public static class Session {
        public final String userId;
        public final String token;
        public final long expiresAt;
        public final String userType;
        public final Date createdAt;
        
        public Session(String userId, String userType) {
            this.userId = userId;
            this.token = TokenManager.generateSessionToken();
            this.expiresAt = System.currentTimeMillis() + Constants.SESSION_DURATION_MS;
            this.userType = userType;
            this.createdAt = new Date();
        }
        
        public Session(Document doc) {
            this.userId = doc.getString("userId");
            this.token = doc.getString("token");
            this.expiresAt = doc.getLong("expiresAt");
            this.userType = doc.getString("userType");
            this.createdAt = doc.getDate("createdAt");
        }
        
        public boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
        
        public long getRemainingTime() {
            return Math.max(0, expiresAt - System.currentTimeMillis());
        }
        
        public Document toDocument() {
            return new Document()
                .append("userId", userId)
                .append("token", token)
                .append("expiresAt", expiresAt)
                .append("userType", userType)
                .append("createdAt", createdAt)
                .append("expireAt", new Date(expiresAt));
        }
    }
    
    public SessionManager() {
        MongoDatabase database = MongoDBConnector.getInstance().getDatabase();
        this.sessionsCollection = database.getCollection("sessions");
        createIndexes();
    }
    
    /**
     * Créer les index nécessaires avec TTL pour expiration automatique
     */
    private void createIndexes() {
        // Index sur userId pour recherche rapide
        sessionsCollection.createIndex(
            Indexes.ascending("userId"),
            new IndexOptions().unique(true)
        );
        
        // Index TTL pour suppression automatique des sessions expirées
        sessionsCollection.createIndex(
            Indexes.ascending("expireAt"),
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS)
        );
        
        // Index sur token pour validation
        sessionsCollection.createIndex(Indexes.ascending("token"));
        
        System.out.println("✅ Index de sessions créés avec TTL automatique");
    }
    
    /**
     * Créer une nouvelle session avec persistance MongoDB
     */
    public Session createSession(String userId, String userType) {
        Session session = new Session(userId, userType);
        
        // Supprimer l'ancienne session si elle existe
        sessionsCollection.deleteOne(Filters.eq("userId", userId));
        
        // Insérer la nouvelle session
        sessionsCollection.insertOne(session.toDocument());
        
        System.out.println("✅ Session créée et persistée: " + userId + 
                         " (expire dans " + (Constants.SESSION_DURATION_MS / 1000 / 60) + " min)");
        
        return session;
    }
    
    /**
     * Valider une session avec vérification de token et expiration
     */
    public boolean validateSession(String userId, String token) {
        Document sessionDoc = sessionsCollection.find(
            Filters.eq("userId", userId)
        ).first();
        
        if (sessionDoc == null) {
            System.out.println("⚠️ Session introuvable: " + userId);
            return false;
        }
        
        Session session = new Session(sessionDoc);
        
        if (!session.token.equals(token)) {
            System.out.println("⚠️ Token invalide pour: " + userId);
            return false;
        }
        
        if (!session.isValid()) {
            sessionsCollection.deleteOne(Filters.eq("userId", userId));
            System.out.println("⚠️ Session expirée et supprimée: " + userId);
            return false;
        }
        
        return true;
    }
    
    /**
     * Obtenir une session active
     */
    public Session getSession(String userId) {
        Document sessionDoc = sessionsCollection.find(
            Filters.eq("userId", userId)
        ).first();
        
        if (sessionDoc == null) {
            return null;
        }
        
        Session session = new Session(sessionDoc);
        
        if (!session.isValid()) {
            sessionsCollection.deleteOne(Filters.eq("userId", userId));
            return null;
        }
        
        return session;
    }
    
    /**
     * Renouveler une session existante
     */
    public Session renewSession(String userId) {
        Session existingSession = getSession(userId);
        
        if (existingSession != null) {
            // Créer une nouvelle session avec le même userType
            return createSession(userId, existingSession.userType);
        }
        
        return null;
    }
    
    /**
     * Détruire une session (logout)
     */
    public void destroySession(String userId) {
        long deletedCount = sessionsCollection.deleteOne(
            Filters.eq("userId", userId)
        ).getDeletedCount();
        
        if (deletedCount > 0) {
            System.out.println("✅ Session détruite: " + userId);
        }
    }
    
    /**
     * Nettoyer manuellement les sessions expirées
     * (En plus du TTL automatique de MongoDB)
     */
    public void cleanupExpiredSessions() {
        long deletedCount = sessionsCollection.deleteMany(
            Filters.lt("expiresAt", System.currentTimeMillis())
        ).getDeletedCount();
        
        if (deletedCount > 0) {
            System.out.println("🧹 " + deletedCount + " session(s) expirée(s) nettoyée(s)");
        }
    }
    
    /**
     * Obtenir le nombre de sessions actives
     */
    public long getActiveSessionsCount() {
        return sessionsCollection.countDocuments(
            Filters.gt("expiresAt", System.currentTimeMillis())
        );
    }
    
    /**
     * Obtenir les statistiques des sessions
     */
    public SessionStats getSessionStats() {
        long totalSessions = sessionsCollection.countDocuments();
        long activeSessions = getActiveSessionsCount();
        long customerSessions = sessionsCollection.countDocuments(
            Filters.and(
                Filters.eq("userType", "CUSTOMER"),
                Filters.gt("expiresAt", System.currentTimeMillis())
            )
        );
        long adminSessions = sessionsCollection.countDocuments(
            Filters.and(
                Filters.eq("userType", "ADMIN"),
                Filters.gt("expiresAt", System.currentTimeMillis())
            )
        );
        
        return new SessionStats(totalSessions, activeSessions, customerSessions, adminSessions);
    }
    
    public static class SessionStats {
        public final long total;
        public final long active;
        public final long customers;
        public final long admins;
        
        public SessionStats(long total, long active, long customers, long admins) {
            this.total = total;
            this.active = active;
            this.customers = customers;
            this.admins = admins;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Sessions: Total=%d, Active=%d (Clients=%d, Admins=%d)",
                total, active, customers, admins
            );
        }
    }
}