// src/main/java/com/skybooking/database/repositories/FavoritesRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 💙 Repository pour la gestion des destinations favorites
 * 
 * Fonctionnalités :
 * - Ajout/suppression de destinations favorites
 * - Récupération par client
 * - Vérification d'existence
 * - Support des statistiques
 */
public class FavoritesRepository extends BaseRepository {
    
    public FavoritesRepository() {
        super(Constants.COLLECTION_FAVORITES);
    }
    
    /**
     * Trouver toutes les destinations favorites d'un client
     */
    public List<Document> findByCustomerId(String customerId) {
        return collection.find(Filters.eq("customerId", customerId))
                        .into(new ArrayList<>());
    }
    
    /**
     * Vérifier si une destination est déjà favorite
     */
    public boolean isFavorite(String customerId, String cityName) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("customerId", customerId),
                Filters.eq("cityName", cityName)
            )
        ) > 0;
    }
    
    /**
     * Ajouter une destination favorite
     */
    public boolean addFavorite(String customerId, String cityName, String countryCode) {
        // Vérifier si déjà favorite
        if (isFavorite(customerId, cityName)) {
            System.out.println("⚠️ Destination déjà favorite: " + cityName);
            return false;
        }
        
        Document favorite = new Document()
            .append("customerId", customerId)
            .append("cityName", cityName)
            .append("countryCode", countryCode)
            .append("addedAt", new Date());
        
        try {
            insert(favorite);
            System.out.println("✅ Destination favorite ajoutée: " + cityName + " pour " + customerId);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Erreur ajout favorite: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Supprimer une destination favorite
     */
    public boolean removeFavorite(String customerId, String cityName) {
        try {
            long deletedCount = collection.deleteOne(
                Filters.and(
                    Filters.eq("customerId", customerId),
                    Filters.eq("cityName", cityName)
                )
            ).getDeletedCount();
            
            if (deletedCount > 0) {
                System.out.println("✅ Destination favorite retirée: " + cityName);
                return true;
            } else {
                System.out.println("⚠️ Destination favorite introuvable: " + cityName);
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur suppression favorite: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Compter les destinations favorites d'un client
     */
    public long countByCustomerId(String customerId) {
        return collection.countDocuments(Filters.eq("customerId", customerId));
    }
    
    /**
     * Obtenir les destinations les plus ajoutées en favoris
     */
    public List<Document> getMostPopularDestinations(int limit) {
        return collection.aggregate(Arrays.asList(
            new Document("$group", new Document("_id", "$cityName")
                .append("count", new Document("$sum", 1))
                .append("countryCode", new Document("$first", "$countryCode"))
            ),
            new Document("$sort", new Document("count", -1)),
            new Document("$limit", limit),
            new Document("$project", new Document()
                .append("cityName", "$_id")
                .append("countryCode", "$countryCode")
                .append("favoriteCount", "$count")
                .append("_id", 0)
            )
        )).into(new ArrayList<>());
    }
    
    /**
     * Supprimer toutes les favorites d'un client
     */
    public long removeAllFavorites(String customerId) {
        return collection.deleteMany(Filters.eq("customerId", customerId))
                        .getDeletedCount();
    }
    
    /**
     * Obtenir les statistiques des destinations favorites
     */
    public Map<String, Object> getFavoritesStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalFavorites", count());
        stats.put("uniqueCustomers", collection.distinct("customerId", String.class)
                                               .into(new ArrayList<>()).size());
        stats.put("uniqueDestinations", collection.distinct("cityName", String.class)
                                                  .into(new ArrayList<>()).size());
        stats.put("mostPopular", getMostPopularDestinations(5));
        
        return stats;
    }
}