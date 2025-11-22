// src/main/java/com/skybooking/database/repositories/TicketRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 🎫 Repository pour la gestion des tickets
 */
public class TicketRepository extends BaseRepository {
    
    public TicketRepository() {
        super(Constants.COLLECTION_TICKETS);
    }
    
    /**
     * 🔍 Trouver un ticket par ID (avec validation et logs)
     */
    public Document findById(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            System.err.println("⚠️ TicketId vide ou null");
            return null;
        }
        
        try {
            Document ticket = collection.find(Filters.eq("ticketId", ticketId)).first();
            
            if (ticket != null) {
                System.out.println("✅ Ticket trouvé : " + ticketId);
            } else {
                System.err.println("❌ Ticket introuvable : " + ticketId);
            }
            
            return ticket;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur MongoDB findById : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 🔍 Trouver un ticket avec détails complets (incluant données passager)
     */
    public Document findByIdWithDetails(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            System.err.println("⚠️ TicketId vide pour findByIdWithDetails");
            return null;
        }
        
        try {
            Document ticket = collection.find(Filters.eq("ticketId", ticketId)).first();
            
            if (ticket != null) {
                // Vérifier que les données passager sont présentes
                if (!ticket.containsKey("passengerDetails")) {
                    System.err.println("⚠️ Ticket sans détails passager : " + ticketId);
                } else {
                    System.out.println("✅ Ticket avec détails complets : " + ticketId);
                }
            } else {
                System.err.println("❌ Ticket introuvable (avec détails) : " + ticketId);
            }
            
            return ticket;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération ticket avec détails : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 🔍 Récupérer le reservationId d'un ticket
     */
    public String getReservationIdByTicketId(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            System.err.println("⚠️ TicketId vide pour getReservationId");
            return null;
        }
        
        try {
            Document ticket = collection.find(Filters.eq("ticketId", ticketId)).first();
            
            if (ticket != null) {
                String reservationId = ticket.getString("reservationId");
                
                if (reservationId != null && !reservationId.isEmpty()) {
                    System.out.println("✅ ReservationId trouvé : " + reservationId + 
                                     " (pour ticket " + ticketId + ")");
                    return reservationId;
                } else {
                    System.err.println("⚠️ Ticket sans reservationId : " + ticketId);
                    return null;
                }
            } else {
                System.err.println("❌ Ticket introuvable pour getReservationId : " + ticketId);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération reservationId : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Trouver les tickets d'une réservation
     */
    public List<Document> findByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            System.err.println("⚠️ ReservationId vide");
            return new ArrayList<>();
        }
        
        try {
            List<Document> tickets = collection.find(Filters.eq("reservationId", reservationId))
                                              .into(new ArrayList<>());
            
            System.out.println("✅ " + tickets.size() + " ticket(s) trouvé(s) pour réservation : " + reservationId);
            return tickets;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur findByReservationId : " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    /**
     * Insérer plusieurs tickets
     */
    public void insertTickets(List<Document> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            System.err.println("⚠️ Liste de tickets vide pour insertion");
            return;
        }
        
        try {
            collection.insertMany(tickets);
            System.out.println("✅ " + tickets.size() + " ticket(s) inséré(s) avec succès");
        } catch (Exception e) {
            System.err.println("❌ Erreur insertion tickets : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Mettre à jour un ticket
     */
    public void updateTicket(String ticketId, Document updates) {
        if (ticketId == null || ticketId.isEmpty()) {
            System.err.println("⚠️ TicketId vide pour mise à jour");
            return;
        }
        
        try {
            updates.append("updatedAt", new Date());
            collection.updateOne(
                Filters.eq("ticketId", ticketId),
                new Document("$set", updates)
            );
            System.out.println("✅ Ticket mis à jour : " + ticketId);
        } catch (Exception e) {
            System.err.println("❌ Erreur mise à jour ticket : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Supprimer les tickets d'une réservation
     */
    public long deleteByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            System.err.println("⚠️ ReservationId vide pour suppression");
            return 0;
        }
        
        try {
            long count = collection.deleteMany(Filters.eq("reservationId", reservationId))
                                  .getDeletedCount();
            System.out.println("✅ " + count + " ticket(s) supprimé(s) pour réservation : " + reservationId);
            return count;
        } catch (Exception e) {
            System.err.println("❌ Erreur suppression tickets : " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Trouver un ticket par siège
     */
    public Document findTicketBySeat(String flightId, String seatNumber) {
        if (flightId == null || flightId.isEmpty() || seatNumber == null || seatNumber.isEmpty()) {
            System.err.println("⚠️ Paramètres invalides pour findTicketBySeat");
            return null;
        }
        
        try {
            Document ticket = collection.find(
                Filters.and(
                    Filters.regex("flightNumber", ".*"),
                    Filters.eq("seatNumber", seatNumber)
                )
            ).first();
            
            if (ticket != null) {
                System.out.println("✅ Ticket trouvé pour siège : " + seatNumber);
            }
            
            return ticket;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur findTicketBySeat : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 🔍 Vérifier si un ticket existe
     */
    public boolean ticketExists(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            return false;
        }
        
        try {
            long count = collection.countDocuments(Filters.eq("ticketId", ticketId));
            return count > 0;
        } catch (Exception e) {
            System.err.println("❌ Erreur ticketExists : " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 📊 Compter les tickets d'une réservation
     */
    public long countByReservationId(String reservationId) {
        if (reservationId == null || reservationId.isEmpty()) {
            return 0;
        }
        
        try {
            return collection.countDocuments(Filters.eq("reservationId", reservationId));
        } catch (Exception e) {
            System.err.println("❌ Erreur countByReservationId : " + e.getMessage());
            return 0;
        }
    }
}