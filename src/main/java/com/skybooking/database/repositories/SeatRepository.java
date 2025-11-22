// src/main/java/com/skybooking/database/repositories/SeatRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 💺 Repository pour la gestion des sièges
 * 
 * Améliorations :
 * - Opérations atomiques avec findAndModify
 * - Mises à jour en lot (bulk operations)
 * - Méthodes de comptage optimisées
 * - Gestion avancée des statuts
 * - Méthodes batch pour réservations atomiques
 */
public class SeatRepository extends BaseRepository {
    
    public SeatRepository() {
        super(Constants.COLLECTION_SEATS);
    }
    
    /**
     * Trouver un siège spécifique
     */
    public Document findSeat(String flightId, String seatNumber) {
        return collection.find(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("seatNumber", seatNumber)
            )
        ).first();
    }
    
    /**
     * Trouver tous les sièges d'un vol
     */
    public List<Document> findSeatsByFlightId(String flightId) {
        return collection.find(Filters.eq("flightId", flightId))
                        .into(new ArrayList<>());
    }
    
    /**
     * Obtenir les sièges disponibles d'un vol
     */
    public List<Document> getAvailableSeats(String flightId, int limit) {
        return collection.find(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("status", "AVAILABLE")
            )
        ).limit(limit).into(new ArrayList<>());
    }
    
    /**
     * Insérer plusieurs sièges
     */
    public void insertSeats(List<Document> seats) {
        if (!seats.isEmpty()) {
            collection.insertMany(seats);
        }
    }
    
    /**
     * Mettre à jour le statut d'un siège
     */
    public boolean updateSeatStatus(String flightId, String seatNumber, String status) {
        Bson update = Updates.combine(
            Updates.set("status", status),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("seatNumber", seatNumber)
            ),
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Mise à jour atomique du statut d'un siège
     * Utilise une condition pour éviter les race conditions
     */
    public boolean updateSeatStatusAtomic(String flightId, String seatNumber, 
                                          String expectedStatus, String newStatus) {
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.eq("seatNumber", seatNumber),
            Filters.eq("status", expectedStatus)
        );
        
        Bson update = Updates.combine(
            Updates.set("status", newStatus),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(filter, update).getModifiedCount() > 0;
    }
    
    /**
     * Mise à jour en lot (bulk) pour performance
     * Permet de réserver plusieurs sièges en une seule opération
     */
    public long updateSeatsStatusBulk(String flightId, List<String> seatNumbers,
                                      String expectedStatus, String newStatus) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return 0;
        }
        
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.in("seatNumber", seatNumbers),
            Filters.eq("status", expectedStatus)
        );
        
        Bson update = Updates.combine(
            Updates.set("status", newStatus),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateMany(filter, update).getModifiedCount();
    }
    
    /**
     * 🔒 RÉSERVER PLUSIEURS SIÈGES DE MANIÈRE ATOMIQUE
     * Utilisée par ReservationManagerImpl
     * 
     * @param flightId ID du vol
     * @param seatNumbers Liste des numéros de sièges
     * @param expectedCount Nombre attendu de sièges (pour validation)
     * @return true si TOUS les sièges ont été réservés avec succès
     */
    public boolean reserveSeatsInBatch(String flightId, List<String> seatNumbers, int expectedCount) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            System.err.println("❌ Liste de sièges vide");
            return false;
        }
        
        System.out.println("→ [SeatRepository] Réservation batch de " + seatNumbers.size() + " siège(s)");
        
        // Mise à jour atomique: AVAILABLE → OCCUPIED
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.in("seatNumber", seatNumbers),
            Filters.eq("status", "AVAILABLE")  // Condition critique
        );
        
        Bson update = Updates.combine(
            Updates.set("status", "OCCUPIED"),
            Updates.set("reservedAt", new Date()),
            Updates.set("updatedAt", new Date())
        );
        
        long modifiedCount = collection.updateMany(filter, update).getModifiedCount();
        
        // Vérification stricte: TOUS les sièges doivent être mis à jour
        boolean success = (modifiedCount == expectedCount);
        
        if (success) {
            System.out.println("✅ [SeatRepository] " + modifiedCount + " siège(s) réservé(s) avec succès");
        } else {
            System.err.println("❌ [SeatRepository] Échec partiel: " + modifiedCount + "/" + expectedCount + " siège(s) réservé(s)");
            System.err.println("   Certains sièges n'étaient plus disponibles");
        }
        
        return success;
    }
    
    /**
     * 🔓 LIBÉRER PLUSIEURS SIÈGES DE MANIÈRE ATOMIQUE
     * Utilisée par ReservationManagerImpl lors d'annulation ou rollback
     * 
     * @param flightId ID du vol
     * @param seatNumbers Liste des numéros de sièges
     * @return true si au moins un siège a été libéré
     */
    public boolean releaseSeatsInBatch(String flightId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            System.err.println("⚠️ Liste de sièges vide - rien à libérer");
            return true;
        }
        
        System.out.println("→ [SeatRepository] Libération batch de " + seatNumbers.size() + " siège(s)");
        
        // Mise à jour: OCCUPIED → AVAILABLE
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.in("seatNumber", seatNumbers),
            Filters.eq("status", "OCCUPIED")  // Libérer uniquement les sièges occupés
        );
        
        Bson update = Updates.combine(
            Updates.set("status", "AVAILABLE"),
            Updates.set("releasedAt", new Date()),
            Updates.set("updatedAt", new Date())
        );
        
        long modifiedCount = collection.updateMany(filter, update).getModifiedCount();
        
        if (modifiedCount > 0) {
            System.out.println("✅ [SeatRepository] " + modifiedCount + " siège(s) libéré(s) avec succès");
            return true;
        } else {
            System.err.println("⚠️ [SeatRepository] Aucun siège libéré (peut-être déjà disponibles)");
            return false;
        }
    }
    
    /**
     * Supprimer tous les sièges d'un vol
     */
    public void deleteSeatsForFlight(String flightId) {
        collection.deleteMany(Filters.eq("flightId", flightId));
    }
    
    /**
     * Compter tous les sièges d'un vol
     */
    public long countSeatsByFlight(String flightId) {
        return collection.countDocuments(Filters.eq("flightId", flightId));
    }
    
    /**
     * Compter les sièges disponibles
     */
    public long countAvailableSeats(String flightId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("status", "AVAILABLE")
            )
        );
    }
    
    /**
     * Compter les sièges occupés
     */
    public long countOccupiedSeats(String flightId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("status", "OCCUPIED")
            )
        );
    }
    
    /**
     * Compter les sièges réservés (non disponibles)
     */
    public long countReservedSeats(String flightId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.ne("status", "AVAILABLE")
            )
        );
    }
    
    /**
     * Vérifier la disponibilité de plusieurs sièges
     * Optimisé pour les réservations multiples
     */
    public boolean areSeatsAvailable(String flightId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return false;
        }
        
        long availableCount = collection.countDocuments(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.in("seatNumber", seatNumbers),
                Filters.eq("status", "AVAILABLE")
            )
        );
        
        return availableCount == seatNumbers.size();
    }
    
    /**
     * Obtenir les sièges d'une classe spécifique
     */
    public List<Document> getSeatsByClass(String flightId, String seatClass) {
        return collection.find(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("seatClass", seatClass)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Obtenir les sièges disponibles par classe
     */
    public List<Document> getAvailableSeatsByClass(String flightId, String seatClass) {
        return collection.find(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("seatClass", seatClass),
                Filters.eq("status", "AVAILABLE")
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Mettre à jour le prix d'un siège
     */
    public boolean updateSeatPrice(String flightId, String seatNumber, double newPrice) {
        Bson update = Updates.combine(
            Updates.set("price", newPrice),
            Updates.set("priceUpdatedAt", new Date())
        );
        
        return collection.updateOne(
            Filters.and(
                Filters.eq("flightId", flightId),
                Filters.eq("seatNumber", seatNumber)
            ),
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Mettre à jour les prix en lot
     */
    public long updateSeatsPriceBulk(String flightId, String seatClass, double newPrice) {
        Bson filter = Filters.and(
            Filters.eq("flightId", flightId),
            Filters.eq("seatClass", seatClass)
        );
        
        Bson update = Updates.combine(
            Updates.set("price", newPrice),
            Updates.set("priceUpdatedAt", new Date())
        );
        
        return collection.updateMany(filter, update).getModifiedCount();
    }
    
    /**
     * Obtenir les statistiques par classe
     */
    public Map<String, SeatClassStats> getSeatStatsByClass(String flightId) {
        Map<String, SeatClassStats> stats = new HashMap<>();
        
        String[] classes = {"ECONOMY", "BUSINESS", "FIRST_CLASS"};
        
        for (String seatClass : classes) {
            long total = collection.countDocuments(
                Filters.and(
                    Filters.eq("flightId", flightId),
                    Filters.eq("seatClass", seatClass)
                )
            );
            
            long available = collection.countDocuments(
                Filters.and(
                    Filters.eq("flightId", flightId),
                    Filters.eq("seatClass", seatClass),
                    Filters.eq("status", "AVAILABLE")
                )
            );
            
            if (total > 0) {
                stats.put(seatClass, new SeatClassStats(
                    seatClass, total, available, total - available
                ));
            }
        }
        
        return stats;
    }
    
    /**
     * Classe pour les statistiques par classe de siège
     */
    public static class SeatClassStats {
        public final String seatClass;
        public final long totalSeats;
        public final long availableSeats;
        public final long occupiedSeats;
        public final double occupancyRate;
        
        public SeatClassStats(String seatClass, long totalSeats, 
                            long availableSeats, long occupiedSeats) {
            this.seatClass = seatClass;
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.occupiedSeats = occupiedSeats;
            this.occupancyRate = totalSeats > 0 
                ? (double) occupiedSeats / totalSeats * 100 
                : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s: %d/%d occupés (%.1f%%)",
                seatClass, occupiedSeats, totalSeats, occupancyRate
            );
        }
    }
}