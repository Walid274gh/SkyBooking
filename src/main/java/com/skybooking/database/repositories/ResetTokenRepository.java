// src/main/java/com/skybooking/database/repositories/ResetTokenRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import com.skybooking.utils.Constants;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 🔑 Repository pour les tokens de réinitialisation de mot de passe
 * Avec expiration automatique (TTL) et gestion sécurisée
 */
public class ResetTokenRepository extends BaseRepository {
    
    public ResetTokenRepository() {
        super("password_reset_tokens");
        createIndexes();
    }
    
    /**
     * Créer les index nécessaires avec TTL pour expiration automatique
     */
    private void createIndexes() {
        // Index unique sur le token
        collection.createIndex(
            Indexes.ascending("token"),
            new IndexOptions().unique(true)
        );
        
        // Index TTL pour suppression automatique des tokens expirés
        collection.createIndex(
            Indexes.ascending("expireAt"),
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS)
        );
        
        // Index sur customerId pour recherche rapide
        collection.createIndex(Indexes.ascending("customerId"));
        
        // Index sur used pour requêtes optimisées
        collection.createIndex(Indexes.ascending("used"));
        
        System.out.println("✅ Index de reset tokens créés avec TTL automatique");
    }
    
    /**
     * Trouver un token par sa valeur
     */
    public Document findByToken(String token) {
        return collection.find(Filters.eq("token", token)).first();
    }
    
    /**
     * Insérer un nouveau token
     */
    public void insertResetToken(Document token) {
        if (!token.containsKey("createdAt")) {
            token.append("createdAt", new Date());
        }
        insert(token);
    }
    
    /**
     * Marquer un token comme utilisé
     */
    public void markTokenAsUsed(String token) {
        collection.updateOne(
            Filters.eq("token", token),
            Updates.combine(
                Updates.set("used", true),
                Updates.set("usedAt", new Date())
            )
        );
    }
    
    /**
     * Invalider tous les tokens d'un utilisateur
     * Utilisé lors d'une nouvelle demande de réinitialisation
     */
    public void invalidateTokensForUser(String customerId) {
        collection.updateMany(
            Filters.and(
                Filters.eq("customerId", customerId),
                Filters.eq("used", false)
            ),
            Updates.set("used", true)
        );
    }
    
    /**
     * Supprimer un token spécifique
     */
    public boolean deleteToken(String token) {
        return collection.deleteOne(Filters.eq("token", token))
                        .getDeletedCount() > 0;
    }
    
    /**
     * Supprimer tous les tokens d'un utilisateur
     */
    public long deleteTokensForUser(String customerId) {
        return collection.deleteMany(Filters.eq("customerId", customerId))
                        .getDeletedCount();
    }
    
    /**
     * Compter les tokens actifs (non utilisés et non expirés)
     */
    public long countActiveTokens() {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("used", false),
                Filters.gt("expiresAt", System.currentTimeMillis())
            )
        );
    }
    
    /**
     * Vérifier si un utilisateur a un token actif
     */
    public boolean hasActiveToken(String customerId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("customerId", customerId),
                Filters.eq("used", false),
                Filters.gt("expiresAt", System.currentTimeMillis())
            )
        ) > 0;
    }
    
    /**
     * Nettoyer manuellement les tokens expirés
     * (MongoDB TTL le fait automatiquement, mais ceci est un backup)
     */
    public long cleanupExpiredTokens() {
        return collection.deleteMany(
            Filters.lt("expiresAt", System.currentTimeMillis())
        ).getDeletedCount();
    }
}