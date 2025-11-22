// src/main/java/com/skybooking/database/repositories/ReservationRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 📋 Repository pour la gestion des réservations
 */
public class ReservationRepository extends BaseRepository {
    
    public ReservationRepository() {
        super(Constants.COLLECTION_RESERVATIONS);
    }
    
    /**
     * Trouver une réservation par ID
     */
    public Document findById(String reservationId) {
        return collection.find(Filters.eq("reservationId", reservationId)).first();
    }
    
    /**
     * Trouver les réservations d'un client
     */
    public List<Document> findByCustomerId(String customerId) {
        return collection.find(Filters.eq("customerId", customerId))
                        .sort(Sorts.descending("createdAt"))
                        .into(new ArrayList<>());
    }
    
    /**
     * Insérer une nouvelle réservation
     */
    public void insertReservation(Document reservation) {
        if (!reservation.containsKey("createdAt")) {
            reservation.append("createdAt", new Date());
        }
        insert(reservation);
    }
    
    /**
     * Mettre à jour le statut d'une réservation
     */
    public void updateStatus(String reservationId, String status) {
        collection.updateOne(
            Filters.eq("reservationId", reservationId),
            Updates.combine(
                Updates.set("status", status),
                Updates.set("updatedAt", new Date())
            )
        );
    }
    
    /**
     * Mettre à jour une réservation
     */
    public void updateReservation(String reservationId, Document updates) {
        updates.append("updatedAt", new Date());
        collection.updateOne(
            Filters.eq("reservationId", reservationId),
            new Document("$set", updates)
        );
    }
    
    /**
     * Supprimer une réservation
     */
    public boolean deleteReservation(String reservationId) {
        return collection.deleteOne(Filters.eq("reservationId", reservationId))
                        .getDeletedCount() > 0;
    }
    
    /**
     * Marquer une réservation comme payée
     */
    public void markAsPaid(String reservationId, String paymentId) {
        collection.updateOne(
            Filters.eq("reservationId", reservationId),
            Updates.combine(
                Updates.set("paymentId", paymentId),
                Updates.set("paymentStatus", "PAID"),
                Updates.set("updatedAt", new Date())
            )
        );
    }
    
    /**
     * Obtenir toutes les réservations
     */
    public List<Document> getAllReservations() {
        return collection.find()
                        .sort(Sorts.descending("createdAt"))
                        .into(new ArrayList<>());
    }
    
    /**
     * Obtenir les réservations par statut
     */
    public List<Document> getReservationsByStatus(String status) {
        return collection.find(Filters.eq("status", status))
                        .sort(Sorts.descending("createdAt"))
                        .into(new ArrayList<>());
    }
    
    /**
     * Obtenir les réservations par plage de dates
     */
    public List<Document> getReservationsByDateRange(String startDate, String endDate) {
        return collection.find(
            Filters.and(
                Filters.gte("reservationDate", startDate),
                Filters.lte("reservationDate", endDate)
            )
        ).sort(Sorts.descending("createdAt"))
         .into(new ArrayList<>());
    }
    
    /**
     * Compter les réservations d'un client
     */
    public long countByCustomerId(String customerId) {
        return collection.countDocuments(Filters.eq("customerId", customerId));
    }
    
    /**
     * Compter les réservations actives pour un vol
     */
    public long countActiveBookingsForFlight(String flightId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("status", "CONFIRMED")
            )
        );
    }
    
    /**
     * Obtenir les réservations actives d'un vol
     */
    public List<Document> getActiveReservationsForFlight(String flightId) {
        return collection.find(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("status", "CONFIRMED")
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Compter les réservations du jour
     */
    public long countTodayBookings() {
        String today = com.skybooking.utils.DateUtils.getCurrentDate();
        return collection.countDocuments(
            Filters.regex("reservationDate", "^" + today)
        );
    }
    
    /**
     * Calculer le total dépensé par un client
     */
    public double calculateTotalSpentByCustomer(String customerId) {
        List<Document> result = collection.aggregate(Arrays.asList(
            new Document("$match", new Document("customerId", customerId)),
            new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", "$totalPrice")))
        )).into(new ArrayList<>());
        
        return result.isEmpty() ? 0.0 : result.get(0).getDouble("total");
    }
    
    /**
     * Obtenir la date de la dernière réservation d'un client
     */
    public String getLastBookingDate(String customerId) {
        Document lastBooking = collection.find(Filters.eq("customerId", customerId))
            .sort(Sorts.descending("createdAt"))
            .first();
        
        return lastBooking != null ? lastBooking.getString("reservationDate") : "Jamais";
    }
}