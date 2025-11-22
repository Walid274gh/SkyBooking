// src/main/java/com/skybooking/security/RateLimiter.java

package com.skybooking.security;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.skybooking.database.MongoDBConnector;
import org.bson.Document;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 🛡️ Rate Limiter pour prévenir les attaques par force brute
 * 
 * Fonctionnalités :
 * - Limitation des tentatives de connexion
 * - Sliding Window pour comptage précis
 * - Persistance dans MongoDB pour clustering
 * - Cache local pour performance
 * - Auto-nettoyage des anciennes tentatives
 */
public class RateLimiter {
    
    private final MongoCollection<Document> attemptsCollection;
    private final ConcurrentHashMap<String, AttemptCache> localCache;
    
    // Configuration
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long WINDOW_SIZE_MS = TimeUnit.MINUTES.toMillis(15); // 15 minutes
    private static final long LOCKOUT_DURATION_MS = TimeUnit.MINUTES.toMillis(30); // 30 minutes
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(60); // 1 minute
    
    /**
     * Cache local pour éviter les requêtes DB constantes
     */
    private static class AttemptCache {
        long count;
        long lastCheck;
        boolean isBlocked;
        
        AttemptCache(long count, boolean isBlocked) {
            this.count = count;
            this.isBlocked = isBlocked;
            this.lastCheck = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - lastCheck > CACHE_TTL_MS;
        }
    }
    
    public RateLimiter() {
        MongoDatabase database = MongoDBConnector.getInstance().getDatabase();
        this.attemptsCollection = database.getCollection("login_attempts");
        this.localCache = new ConcurrentHashMap<>();
        
        createIndexes();
    }
    
    private void createIndexes() {
        // Index sur identifier (IP ou username)
        attemptsCollection.createIndex(Indexes.ascending("identifier"));
        
        // Index TTL pour nettoyage automatique (après 1 heure)
        attemptsCollection.createIndex(
            Indexes.ascending("expireAt"),
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS)
        );
        
        // Index sur timestamp pour sliding window
        attemptsCollection.createIndex(Indexes.ascending("timestamp"));
        
        System.out.println("✅ Index Rate Limiter créés");
    }
    
    /**
     * Vérifier si une action est autorisée
     * @param identifier IP ou username
     * @return true si autorisé, false si bloqué
     */
    public boolean isAllowed(String identifier) {
        // Vérifier le cache local d'abord
        AttemptCache cached = localCache.get(identifier);
        if (cached != null && !cached.isExpired()) {
            if (cached.isBlocked) {
                System.out.println("⚠️ [CACHE] Accès bloqué pour: " + identifier);
                return false;
            }
            if (cached.count < MAX_LOGIN_ATTEMPTS) {
                return true;
            }
        }
        
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        
        // Compter les tentatives dans la fenêtre de temps
        long attemptCount = attemptsCollection.countDocuments(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.gte("timestamp", windowStart),
                Filters.eq("success", false)
            )
        );
        
        // Vérifier si bloqué
        Document lockout = attemptsCollection.find(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.eq("locked", true),
                Filters.gt("lockoutUntil", now)
            )
        ).first();
        
        boolean isBlocked = (lockout != null || attemptCount >= MAX_LOGIN_ATTEMPTS);
        
        // Mettre à jour le cache
        localCache.put(identifier, new AttemptCache(attemptCount, isBlocked));
        
        if (isBlocked) {
            System.out.println("⚠️ Accès bloqué pour: " + identifier + 
                             " (" + attemptCount + " tentatives)");
            return false;
        }
        
        return true;
    }
    
    /**
     * Enregistrer une tentative de connexion échouée
     */
    public void recordFailedAttempt(String identifier, String ipAddress) {
        long now = System.currentTimeMillis();
        
        Document attempt = new Document()
            .append("identifier", identifier)
            .append("ipAddress", ipAddress)
            .append("timestamp", now)
            .append("success", false)
            .append("expireAt", new Date(now + TimeUnit.HOURS.toMillis(1)));
        
        attemptsCollection.insertOne(attempt);
        
        // Invalider le cache
        localCache.remove(identifier);
        
        // Vérifier si on doit bloquer
        long windowStart = now - WINDOW_SIZE_MS;
        long attemptCount = attemptsCollection.countDocuments(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.gte("timestamp", windowStart),
                Filters.eq("success", false)
            )
        );
        
        if (attemptCount >= MAX_LOGIN_ATTEMPTS) {
            lockAccount(identifier, ipAddress);
        }
        
        System.out.println("⚠️ Tentative échouée enregistrée: " + identifier + 
                         " (" + attemptCount + "/" + MAX_LOGIN_ATTEMPTS + ")");
    }
    
    /**
     * Enregistrer une connexion réussie
     */
    public void recordSuccessfulAttempt(String identifier, String ipAddress) {
        long now = System.currentTimeMillis();
        
        Document attempt = new Document()
            .append("identifier", identifier)
            .append("ipAddress", ipAddress)
            .append("timestamp", now)
            .append("success", true)
            .append("expireAt", new Date(now + TimeUnit.HOURS.toMillis(1)));
        
        attemptsCollection.insertOne(attempt);
        
        // Réinitialiser le compteur en cas de succès
        resetAttempts(identifier);
        
        System.out.println("✅ Connexion réussie: " + identifier);
    }
    
    /**
     * Bloquer un compte temporairement
     */
    private void lockAccount(String identifier, String ipAddress) {
        long now = System.currentTimeMillis();
        long lockoutUntil = now + LOCKOUT_DURATION_MS;
        
        Document lockout = new Document()
            .append("identifier", identifier)
            .append("ipAddress", ipAddress)
            .append("locked", true)
            .append("lockedAt", now)
            .append("lockoutUntil", lockoutUntil)
            .append("reason", "Too many failed login attempts")
            .append("expireAt", new Date(lockoutUntil));
        
        attemptsCollection.insertOne(lockout);
        
        // Mettre à jour le cache
        localCache.put(identifier, new AttemptCache(MAX_LOGIN_ATTEMPTS, true));
        
        System.out.println("🔒 COMPTE BLOQUÉ: " + identifier + 
                         " jusqu'à " + new Date(lockoutUntil));
    }
    
    /**
     * Réinitialiser les tentatives après succès
     */
    public void resetAttempts(String identifier) {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        
        attemptsCollection.deleteMany(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.gte("timestamp", windowStart)
            )
        );
        
        // Invalider le cache
        localCache.remove(identifier);
        
        System.out.println("🔓 Tentatives réinitialisées pour: " + identifier);
    }
    
    /**
     * Débloquer manuellement un compte
     */
    public void unlockAccount(String identifier) {
        attemptsCollection.deleteMany(
            Filters.eq("identifier", identifier)
        );
        
        localCache.remove(identifier);
        
        System.out.println("🔓 Compte débloqué manuellement: " + identifier);
    }
    
    /**
     * Obtenir les informations de blocage
     */
    public LockoutInfo getLockoutInfo(String identifier) {
        long now = System.currentTimeMillis();
        
        Document lockout = attemptsCollection.find(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.eq("locked", true),
                Filters.gt("lockoutUntil", now)
            )
        ).first();
        
        if (lockout != null) {
            long lockoutUntil = lockout.getLong("lockoutUntil");
            long remainingMs = lockoutUntil - now;
            long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
            
            return new LockoutInfo(
                true,
                new Date(lockoutUntil),
                remainingMinutes,
                lockout.getString("reason")
            );
        }
        
        // Pas bloqué, retourner le nombre de tentatives
        long windowStart = now - WINDOW_SIZE_MS;
        long attemptCount = attemptsCollection.countDocuments(
            Filters.and(
                Filters.eq("identifier", identifier),
                Filters.gte("timestamp", windowStart),
                Filters.eq("success", false)
            )
        );
        
        return new LockoutInfo(
            false,
            null,
            0,
            attemptCount + " tentative(s) échouée(s) dans les 15 dernières minutes"
        );
    }
    
    /**
     * Obtenir les statistiques globales
     */
    public RateLimiterStats getStats() {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE_MS;
        
        long totalAttempts = attemptsCollection.countDocuments();
        long recentAttempts = attemptsCollection.countDocuments(
            Filters.gte("timestamp", windowStart)
        );
        long failedAttempts = attemptsCollection.countDocuments(
            Filters.and(
                Filters.gte("timestamp", windowStart),
                Filters.eq("success", false)
            )
        );
        long lockedAccounts = attemptsCollection.countDocuments(
            Filters.and(
                Filters.eq("locked", true),
                Filters.gt("lockoutUntil", now)
            )
        );
        
        return new RateLimiterStats(
            totalAttempts,
            recentAttempts,
            failedAttempts,
            lockedAccounts
        );
    }
    
    /**
     * Nettoyer le cache local
     */
    public void cleanupCache() {
        localCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Classe pour les informations de blocage
     */
    public static class LockoutInfo {
        public final boolean isLocked;
        public final Date lockoutUntil;
        public final long remainingMinutes;
        public final String message;
        
        public LockoutInfo(boolean isLocked, Date lockoutUntil, 
                          long remainingMinutes, String message) {
            this.isLocked = isLocked;
            this.lockoutUntil = lockoutUntil;
            this.remainingMinutes = remainingMinutes;
            this.message = message;
        }
        
        @Override
        public String toString() {
            if (isLocked) {
                return String.format(
                    "Compte bloqué jusqu'à %s (%d minutes restantes)",
                    lockoutUntil, remainingMinutes
                );
            }
            return message;
        }
    }
    
    /**
     * Classe pour les statistiques
     */
    public static class RateLimiterStats {
        public final long totalAttempts;
        public final long recentAttempts;
        public final long failedAttempts;
        public final long lockedAccounts;
        
        public RateLimiterStats(long totalAttempts, long recentAttempts,
                               long failedAttempts, long lockedAccounts) {
            this.totalAttempts = totalAttempts;
            this.recentAttempts = recentAttempts;
            this.failedAttempts = failedAttempts;
            this.lockedAccounts = lockedAccounts;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Total: %d | Récentes: %d | Échouées: %d | Bloqués: %d",
                totalAttempts, recentAttempts, failedAttempts, lockedAccounts
            );
        }
    }
}