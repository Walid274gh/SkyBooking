// src/main/java/com/skybooking/database/repositories/HotelReservationRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import java.util.*;

/**
 * 📋 Repository pour les réservations d'hôtels
 */
public class HotelReservationRepository extends BaseRepository {
    
    public HotelReservationRepository() {
        super("hotel_reservations");
    }
    
    /**
     * 🆕 Insérer une réservation d'hôtel (enveloppe publique pour insert protégé)
     */
    public void insertReservation(Document reservation) {
        if (!reservation.containsKey("createdAt")) {
            reservation.append("createdAt", new Date());
        }
        if (!reservation.containsKey("updatedAt")) {
            reservation.append("updatedAt", new Date());
        }
        insert(reservation);
    }
    
    /**
     * Trouver une réservation par ID
     */
    public Document findById(String hotelReservationId) {
        return collection.find(
            Filters.eq("hotelReservationId", hotelReservationId)
        ).first();
    }
    
    /**
     * Trouver toutes les réservations d'un client
     */
    public List<Document> findByCustomerId(String customerId) {
        return collection.find(Filters.eq("customerId", customerId))
                        .into(new ArrayList<>());
    }
    
    /**
     * Trouver les réservations liées à un vol
     */
    public List<Document> findByFlightReservation(String flightReservationId) {
        return collection.find(
            Filters.eq("flightReservationId", flightReservationId)
        ).into(new ArrayList<>());
    }
    
    /**
     * Mettre à jour le statut d'une réservation
     */
    public boolean updateStatus(String hotelReservationId, String newStatus) {
        return collection.updateOne(
            Filters.eq("hotelReservationId", hotelReservationId),
            Updates.combine(
                Updates.set("status", newStatus),
                Updates.set("updatedAt", new Date())
            )
        ).getModifiedCount() > 0;
    }
    
    /**
     * Supprimer une réservation
     */
    public boolean delete(String hotelReservationId) {
        return collection.deleteOne(
            Filters.eq("hotelReservationId", hotelReservationId)
        ).getDeletedCount() > 0;
    }
    
    /**
     * Compter les réservations d'un client
     */
    public long countByCustomer(String customerId) {
        return collection.countDocuments(
            Filters.eq("customerId", customerId)
        );
    }
    
    /**
     * Obtenir toutes les réservations actives
     */
    public List<Document> getActiveReservations() {
        return collection.find(
            Filters.in("status", Arrays.asList("PENDING", "CONFIRMED"))
        ).into(new ArrayList<>());
    }
    
    // ==================== MÉTHODES ADMIN ====================
    
    /**
     * Trouver toutes les réservations
     */
    public List<Document> findAll() {
        return collection.find().into(new ArrayList<>());
    }
    
    /**
     * Trouver par statut
     */
    public List<Document> findByStatus(String status) {
        return collection.find(Filters.eq("status", status))
                        .into(new ArrayList<>());
    }
    
    /**
     * Trouver par plage de dates
     */
    public List<Document> findByDateRange(String startDate, String endDate) {
        return collection.find(
            Filters.and(
                Filters.gte("checkInDate", startDate),
                Filters.lte("checkOutDate", endDate)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Trouver par hôtel
     */
    public List<Document> findByHotelId(String hotelId) {
        return collection.find(Filters.eq("hotelId", hotelId))
                        .into(new ArrayList<>());
    }
    
    /**
     * Trouver par hôtel et statuts
     */
    public List<Document> findByHotelIdAndStatus(String hotelId, List<String> statuses) {
        return collection.find(
            Filters.and(
                Filters.eq("hotelId", hotelId),
                Filters.in("status", statuses)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Calculer le revenu total
     */
    public double calculateTotalRevenue() {
        List<Document> completedBookings = collection.find(
            Filters.eq("status", "CONFIRMED")
        ).into(new ArrayList<>());
        
        double total = 0;
        for (Document booking : completedBookings) {
            total += booking.getDouble("finalPrice");
        }
        
        return total;
    }
    
    /**
     * Obtenir la ville la plus populaire
     */
    public String getTopCity() {
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$lookup", new Document()
                .append("from", "hotels")
                .append("localField", "hotelId")
                .append("foreignField", "hotelId")
                .append("as", "hotelInfo")
            ),
            new Document("$unwind", "$hotelInfo"),
            new Document("$group", new Document("_id", "$hotelInfo.city")
                .append("count", new Document("$sum", 1))
            ),
            new Document("$sort", new Document("count", -1)),
            new Document("$limit", 1)
        )).into(new ArrayList<>());
        
        return results.isEmpty() ? "N/A" : results.get(0).getString("_id");
    }
    
    /**
     * Obtenir l'hôtel le plus populaire
     */
    public String getTopHotel() {
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$group", new Document("_id", "$hotelName")
                .append("count", new Document("$sum", 1))
            ),
            new Document("$sort", new Document("count", -1)),
            new Document("$limit", 1)
        )).into(new ArrayList<>());
        
        return results.isEmpty() ? "N/A" : results.get(0).getString("_id");
    }
    
    /**
     * Obtenir le top N des hôtels
     */
    public List<String> getTopHotels(int limit) {
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$group", new Document("_id", "$hotelName")
                .append("bookings", new Document("$sum", 1))
                .append("revenue", new Document("$sum", "$finalPrice"))
            ),
            new Document("$sort", new Document("bookings", -1)),
            new Document("$limit", limit)
        )).into(new ArrayList<>());
        
        List<String> topHotels = new ArrayList<>();
        for (Document doc : results) {
            topHotels.add(doc.getString("_id"));
        }
        
        return topHotels;
    }
    
    /**
     * Obtenir le top N des villes
     */
    public List<String> getTopCities(int limit) {
        List<Document> results = collection.aggregate(Arrays.asList(
            new Document("$lookup", new Document()
                .append("from", "hotels")
                .append("localField", "hotelId")
                .append("foreignField", "hotelId")
                .append("as", "hotelInfo")
            ),
            new Document("$unwind", "$hotelInfo"),
            new Document("$group", new Document("_id", "$hotelInfo.city")
                .append("bookings", new Document("$sum", 1))
            ),
            new Document("$sort", new Document("bookings", -1)),
            new Document("$limit", limit)
        )).into(new ArrayList<>());
        
        List<String> topCities = new ArrayList<>();
        for (Document doc : results) {
            topCities.add(doc.getString("_id"));
        }
        
        return topCities;
    }
}