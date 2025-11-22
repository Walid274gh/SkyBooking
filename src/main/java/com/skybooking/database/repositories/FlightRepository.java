// src/main/java/com/skybooking/database/repositories/FlightRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.skybooking.utils.Constants;
import com.skybooking.utils.DateUtils;
import java.util.*;

/**
 * ✈️ Repository pour la gestion des vols
 * 
 * Améliorations :
 * - Opérations atomiques pour les compteurs
 * - Support des incréments/décréments par quantité
 * - Méthodes de recherche optimisées
 * - Gestion avancée de la cohérence
 * - Insertion en batch pour performance
 */
public class FlightRepository extends BaseRepository {
    
    public FlightRepository() {
        super(Constants.COLLECTION_FLIGHTS);
    }
    
    /**
     * Trouver un vol par ID
     */
    public Document findById(String flightId) {
        return collection.find(Filters.eq("flightId", flightId)).first();
    }
    
    /**
     * Insérer un nouveau vol
     */
    public void insertFlight(Document flight) {
        if (!flight.containsKey("createdAt")) {
            flight.append("createdAt", new Date());
        }
        insert(flight);
    }
    
    /**
     * ⭐ NOUVEAU: Insérer plusieurs vols en batch pour performance
     * Utilisé lors de l'initialisation de milliers de vols
     */
    public void insertFlightsBatch(List<Document> flights) {
        if (flights == null || flights.isEmpty()) {
            return;
        }
        
        // Ajouter createdAt à tous les vols
        Date now = new Date();
        for (Document flight : flights) {
            if (!flight.containsKey("createdAt")) {
                flight.append("createdAt", now);
            }
        }
        
        // Insertion par lots de 1000 pour éviter les timeouts
        int batchSize = 1000;
        int totalFlights = flights.size();
        int processedCount = 0;
        
        try {
            for (int i = 0; i < totalFlights; i += batchSize) {
                int endIndex = Math.min(i + batchSize, totalFlights);
                List<Document> batch = flights.subList(i, endIndex);
                
                // Insertion avec options (ordered=false pour continuer en cas d'erreur)
                InsertManyOptions options = new InsertManyOptions().ordered(false);
                collection.insertMany(batch, options);
                
                processedCount += batch.size();
                
                // Log de progression
                if (processedCount % 5000 == 0 || processedCount == totalFlights) {
                    System.out.println("💾 Insertion batch: " + processedCount + "/" + 
                                     totalFlights + " vols insérés");
                }
            }
            
            System.out.println("✅ Insertion batch terminée: " + processedCount + 
                             " vols dans MongoDB");
            
        } catch (Exception e) {
            System.err.println("⚠️ Erreur lors de l'insertion batch: " + e.getMessage());
            System.err.println("   " + processedCount + " vols insérés avant l'erreur");
            // On ne lance pas l'exception pour permettre au système de continuer
        }
    }
    
    /**
     * Rechercher des vols avec filtres
     */
    public List<Document> searchFlights(String departureCity, String arrivalCity, 
                                       String date, String seatClass) {
        List<Bson> filters = new ArrayList<>();
        
        if (departureCity != null && !departureCity.isEmpty()) {
            filters.add(Filters.regex("departureCity", departureCity, "i"));
        }
        
        if (arrivalCity != null && !arrivalCity.isEmpty()) {
            filters.add(Filters.regex("arrivalCity", arrivalCity, "i"));
        }
        
        if (date != null && !date.isEmpty()) {
            // Vérifier que la date n'est pas dans le passé
            if (DateUtils.isPastDate(date)) {
                return new ArrayList<>();
            }
            filters.add(Filters.eq("departureDate", date));
        }
        
        filters.add(Filters.gt("availableSeats", 0));
        
        Bson combinedFilter = filters.isEmpty() 
            ? new Document() 
            : Filters.and(filters);
        
        return collection.find(combinedFilter).into(new ArrayList<>());
    }
    
    /**
     * Rechercher des vols similaires (pour alternatives)
     */
    public List<Document> searchSimilarFlights(String departureCity, String arrivalCity, 
                                               String departureDate, String excludeFlightId) {
        List<Bson> filters = new ArrayList<>();
        
        filters.add(Filters.regex("departureCity", departureCity, "i"));
        filters.add(Filters.regex("arrivalCity", arrivalCity, "i"));
        filters.add(Filters.ne("flightId", excludeFlightId));
        filters.add(Filters.gt("availableSeats", 0));
        
        // Chercher ±3 jours autour de la date
        try {
            Date targetDate = DateUtils.parseDate(departureDate);
            Date dateFrom = DateUtils.addDays(targetDate, -3);
            Date dateTo = DateUtils.addDays(targetDate, 3);
            
            filters.add(Filters.gte("departureDate", DateUtils.formatDate(dateFrom)));
            filters.add(Filters.lte("departureDate", DateUtils.formatDate(dateTo)));
        } catch (Exception e) {
            filters.add(Filters.eq("departureDate", departureDate));
        }
        
        return collection.find(Filters.and(filters))
                        .limit(10)
                        .into(new ArrayList<>());
    }
    
    /**
     * Décrémenter les sièges disponibles de manière atomique
     * Version avec quantité (1 par défaut)
     */
    public boolean decrementAvailableSeats(String flightId) {
        return decrementAvailableSeats(flightId, 1);
    }
    
    /**
     * Décrémenter par quantité spécifique
     * Pour les réservations multiples
     */
    public boolean decrementAvailableSeats(String flightId, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.gte("availableSeats", quantity)
        );
        
        Bson update = Updates.combine(
            Updates.inc("availableSeats", -quantity),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(filter, update).getModifiedCount() > 0;
    }
    
    /**
     * Incrémenter les sièges disponibles
     */
    public boolean incrementAvailableSeats(String flightId) {
        return incrementAvailableSeats(flightId, 1);
    }
    
    /**
     * Incrémenter par quantité spécifique
     */
    public boolean incrementAvailableSeats(String flightId, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        
        Bson update = Updates.combine(
            Updates.inc("availableSeats", quantity),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(
            Filters.eq("flightId", flightId), 
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Mettre à jour un vol
     */
    public void updateFlight(String flightId, Document updates) {
        updates.append("updatedAt", new Date());
        collection.updateOne(
            Filters.eq("flightId", flightId),
            new Document("$set", updates)
        );
    }
    
    /**
     * Supprimer un vol
     */
    public boolean deleteFlight(String flightId) {
        return collection.deleteOne(Filters.eq("flightId", flightId))
                        .getDeletedCount() > 0;
    }
    
    /**
     * Vérifier si un vol est valide (pas dans le passé)
     */
    public boolean isFlightValid(String flightId) {
        Document flight = findById(flightId);
        if (flight == null) return false;
        
        return DateUtils.isFutureDate(flight.getString("departureDate"));
    }
    
    /**
     * Vérifier si un numéro de vol existe pour une date donnée
     */
    public boolean flightNumberExists(String flightNumber, String departureDate) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("flightNumber", flightNumber),
                Filters.eq("departureDate", departureDate)
            )
        ) > 0;
    }
    
    /**
     * Obtenir tous les vols
     */
    public List<Document> getAllFlights() {
        return collection.find().into(new ArrayList<>());
    }
    
    /**
     * Obtenir les vols avec incohérence
     * Pour le monitoring et la maintenance
     */
    public List<Document> findInconsistentFlights() {
        // Cette méthode nécessite une agrégation avec lookup
        // Pour simplifier, on retourne une liste vide
        // À implémenter avec aggregation pipeline si nécessaire
        return new ArrayList<>();
    }
    
    /**
     * Mettre à jour le compteur de sièges disponibles
     * Basé sur le comptage réel
     */
    public boolean syncAvailableSeats(String flightId, int actualCount) {
        Bson update = Updates.combine(
            Updates.set("availableSeats", actualCount),
            Updates.set("lastSyncAt", new Date())
        );
        
        return collection.updateOne(
            Filters.eq("flightId", flightId),
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Obtenir les vols par statut de disponibilité
     */
    public List<Document> getFlightsByAvailability(int minSeats, int maxSeats) {
        return collection.find(
            Filters.and(
                Filters.gte("availableSeats", minSeats),
                Filters.lte("availableSeats", maxSeats)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Obtenir les vols complets (plus de sièges disponibles)
     */
    public List<Document> getFullFlights() {
        return collection.find(
            Filters.eq("availableSeats", 0)
        ).into(new ArrayList<>());
    }
    
    /**
     * Obtenir les vols avec peu de sièges disponibles
     */
    public List<Document> getLowAvailabilityFlights(int threshold) {
        return collection.find(
            Filters.and(
                Filters.gt("availableSeats", 0),
                Filters.lte("availableSeats", threshold)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Compter les vols par ville de départ
     */
    public Map<String, Long> countFlightsByDepartureCity() {
        Map<String, Long> stats = new HashMap<>();
        
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$group", new Document("_id", "$departureCity")
                .append("count", new Document("$sum", 1))),
            new Document("$sort", new Document("count", -1))
        )).into(new ArrayList<>());
        
        for (Document doc : results) {
            stats.put(doc.getString("_id"), 
                     ((Number) doc.get("count")).longValue());
        }
        
        return stats;
    }
    
    /**
     * Calculer le taux d'occupation moyen
     */
    public double calculateAverageOccupancyRate() {
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$project", new Document()
                .append("totalCapacity", new Document("$add", Arrays.asList(
                    "$availableSeats",
                    new Document("$subtract", Arrays.asList(150, "$availableSeats"))
                )))
                .append("occupiedSeats", new Document("$subtract", Arrays.asList(
                    150, "$availableSeats"
                )))
            ),
            new Document("$group", new Document("_id", null)
                .append("avgOccupancy", new Document("$avg", 
                    new Document("$multiply", Arrays.asList(
                        new Document("$divide", Arrays.asList(
                            "$occupiedSeats", "$totalCapacity"
                        )),
                        100
                    ))
                ))
            )
        )).into(new ArrayList<>());
        
        if (results.isEmpty()) {
            return 0.0;
        }
        
        return results.get(0).getDouble("avgOccupancy");
    }
    
    /**
     * Obtenir les vols les plus populaires
     */
    public List<Document> getMostPopularFlights(int limit) {
        return collection.aggregate(Arrays.asList(
            new Document("$addFields", new Document()
                .append("bookingCount", new Document("$subtract", Arrays.asList(
                    150, "$availableSeats"
                )))
            ),
            new Document("$sort", new Document("bookingCount", -1)),
            new Document("$limit", limit)
        )).into(new ArrayList<>());
    }
    
    /**
     * Mettre à jour les prix d'un vol
     */
    public boolean updateFlightPrices(String flightId, 
                                     double economyPrice, 
                                     double businessPrice, 
                                     double firstClassPrice) {
        Bson update = Updates.combine(
            Updates.set("economyPrice", economyPrice),
            Updates.set("businessPrice", businessPrice),
            Updates.set("firstClassPrice", firstClassPrice),
            Updates.set("pricesUpdatedAt", new Date())
        );
        
        return collection.updateOne(
            Filters.eq("flightId", flightId),
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Appliquer une réduction sur tous les vols d'une route
     */
    public long applyDiscountToRoute(String departureCity, String arrivalCity, 
                                    double discountPercentage) {
        if (discountPercentage <= 0 || discountPercentage >= 100) {
            return 0;
        }
        
        double multiplier = 1 - (discountPercentage / 100);
        
        List<Document> flights = collection.find(
            Filters.and(
                Filters.eq("departureCity", departureCity),
                Filters.eq("arrivalCity", arrivalCity)
            )
        ).into(new ArrayList<>());
        
        long updatedCount = 0;
        
        for (Document flight : flights) {
            double newEconomy = flight.getDouble("economyPrice") * multiplier;
            double newBusiness = flight.getDouble("businessPrice") * multiplier;
            double newFirstClass = flight.getDouble("firstClassPrice") * multiplier;
            
            if (updateFlightPrices(flight.getString("flightId"), 
                                  newEconomy, newBusiness, newFirstClass)) {
                updatedCount++;
            }
        }
        
        return updatedCount;
    }
    
    /**
     * Supprimer tous les vols (pour réinitialisation)
     * ATTENTION: Utiliser avec précaution!
     */
    public long deleteAllFlights() {
        return collection.deleteMany(new Document()).getDeletedCount();
    }
    
    /**
     * Obtenir statistiques de la base de données
     */
    public Map<String, Object> getDatabaseStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalFlights", count());
        stats.put("flightsWithSeats", collection.countDocuments(Filters.gt("availableSeats", 0)));
        stats.put("fullFlights", collection.countDocuments(Filters.eq("availableSeats", 0)));
        stats.put("averageOccupancy", calculateAverageOccupancyRate());
        stats.put("flightsByCity", countFlightsByDepartureCity());
        
        return stats;
    }
}