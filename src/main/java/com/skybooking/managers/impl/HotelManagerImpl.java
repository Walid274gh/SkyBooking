// src/main/java/com/skybooking/managers/impl/HotelManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.HotelRepository;
import com.skybooking.database.repositories.HotelReservationRepository;
import com.skybooking.database.repositories.PaymentRepository;
import com.skybooking.database.repositories.RefundRepository;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.security.TokenManager;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 🏨 GESTIONNAIRE D'HÔTELS - DYNAMIC PACKAGING
 * Implémente la réduction automatique de 15% si lié à une réservation de vol
 */
public class HotelManagerImpl extends HotelManagerPOA {
    
    private final HotelRepository hotelRepository;
    private final HotelReservationRepository hotelReservationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ReservationManagerImpl reservationManager;
    
    // Configuration de la réduction Dynamic Packaging
    private static final double FLIGHT_DISCOUNT_PERCENTAGE = 15.0;
    
    // ✅ Politique d'annulation hôtel (heures avant check-in)
    private static final long HOURS_FREE_CANCELLATION = 48; // 48h avant = remboursement 100%
    private static final long HOURS_PARTIAL_REFUND = 24;    // 24h avant = remboursement 50%
    private static final double PARTIAL_REFUND_PERCENTAGE = 50.0;
    
    public HotelManagerImpl(ReservationManagerImpl reservationManager) {
        this.hotelRepository = new HotelRepository();
        this.hotelReservationRepository = new HotelReservationRepository();
        this.paymentRepository = new PaymentRepository();
        this.refundRepository = new RefundRepository();
        this.reservationManager = reservationManager;
        
        if (hotelRepository.count() == 0) {
            System.out.println("🏨 Initialisation des données hôtels...");
            hotelRepository.initializeHotels();
        } else {
            System.out.println("✅ Base hôtels initialisée: " + hotelRepository.count() + " hôtels");
        }
    }
    
    @Override
    public Hotel[] searchHotels(
            String city,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms,
            int minStarRating) {
        
        System.out.println("🔍 Recherche hôtels: " + city + 
                         " | " + checkInDate + " → " + checkOutDate +
                         " | " + numberOfRooms + " chambre(s) | " + 
                         minStarRating + "⭐+");
        
        if (!DateUtils.isFutureDate(checkInDate)) {
            System.err.println("❌ Date check-in dans le passé");
            return new Hotel[0];
        }
        
        if (!DateUtils.isAfter(checkOutDate, checkInDate)) {
            System.err.println("❌ Date check-out invalide");
            return new Hotel[0];
        }
        
        List<Document> hotelDocs = hotelRepository.searchHotels(
            city, numberOfRooms, minStarRating
        );
        
        Hotel[] hotels = new Hotel[hotelDocs.size()];
        for (int i = 0; i < hotelDocs.size(); i++) {
            hotels[i] = ManagerHelper.documentToHotel(hotelDocs.get(i));
        }
        
        System.out.println("✅ " + hotels.length + " hôtel(s) trouvé(s)");
        return hotels;
    }
    
    @Override
    public Hotel getHotelById(String hotelId) throws HotelNotFoundException {
        Document doc = hotelRepository.findById(hotelId);
        if (doc == null) {
            throw new HotelNotFoundException("Hôtel non trouvé: " + hotelId);
        }
        return ManagerHelper.documentToHotel(doc);
    }
    
    @Override
    public HotelReservation bookHotel(
            String customerId,
            String hotelId,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms,
            String flightReservationId) 
            throws HotelBookingException, NoRoomsAvailableException {
        
        System.out.println("📝 Réservation hôtel: " + hotelId + 
                         " | Client: " + customerId +
                         " | Vol lié: " + flightReservationId);
        
        try {
            Document hotelDoc = hotelRepository.findById(hotelId);
            if (hotelDoc == null) {
                throw new HotelBookingException("Hôtel non trouvé: " + hotelId);
            }
            
            int availableRooms = hotelDoc.getInteger("availableRooms", 0);
            if (availableRooms < numberOfRooms) {
                throw new NoRoomsAvailableException(
                    "Seulement " + availableRooms + " chambre(s) disponible(s)"
                );
            }
            
            int numberOfNights = DateUtils.calculateNights(checkInDate, checkOutDate);
            if (numberOfNights <= 0) {
                throw new HotelBookingException("Durée de séjour invalide");
            }
            
            double pricePerNight = hotelDoc.getDouble("pricePerNight");
            double originalPrice = pricePerNight * numberOfNights * numberOfRooms;
            
            // 🎯 VÉRIFIER LE DYNAMIC PACKAGING
            double discountPercentage = 0.0;
            boolean hasFlightDiscount = false;
            
            if (flightReservationId != null && !flightReservationId.trim().isEmpty()) {
                hasFlightDiscount = verifyFlightReservation(
                    customerId, 
                    flightReservationId,
                    hotelDoc.getString("city")
                );
                
                if (hasFlightDiscount) {
                    discountPercentage = FLIGHT_DISCOUNT_PERCENTAGE;
                    System.out.println("✨ Dynamic Packaging activé: -" + 
                                     discountPercentage + "%");
                }
            }
            
            double finalPrice = originalPrice * (1 - discountPercentage / 100);
            double savings = originalPrice - finalPrice;
            
            String hotelReservationId = "HR" + System.currentTimeMillis() + 
                                       customerId.hashCode();
            
            // ✅ Créer la réservation en PENDING_PAYMENT
            Document reservation = new Document()
                .append("hotelReservationId", hotelReservationId)
                .append("customerId", customerId)
                .append("hotelId", hotelId)
                .append("hotelName", hotelDoc.getString("hotelName"))
                .append("city", hotelDoc.getString("city"))
                .append("checkInDate", checkInDate)
                .append("checkOutDate", checkOutDate)
                .append("numberOfNights", numberOfNights)
                .append("numberOfRooms", numberOfRooms)
                .append("pricePerNight", pricePerNight)
                .append("originalPrice", originalPrice)
                .append("discountPercentage", discountPercentage)
                .append("finalPrice", finalPrice)
                .append("savings", savings)
                .append("status", "PENDING_PAYMENT")
                .append("paymentStatus", "PENDING")
                .append("reservationDate", DateUtils.getCurrentDateTime())
                .append("flightReservationId", flightReservationId)
                .append("hasFlightDiscount", hasFlightDiscount);
            
            hotelReservationRepository.insertReservation(reservation);
            
            System.out.println("✅ Réservation hôtel créée (en attente de paiement): " + hotelReservationId);
            if (hasFlightDiscount) {
                System.out.println("💰 Économie potentielle: " + 
                    String.format("%.2f DZD (-%.0f%%)", savings, discountPercentage));
            }
            
            return ManagerHelper.documentToHotelReservation(reservation);
            
        } catch (HotelBookingException | NoRoomsAvailableException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur réservation hôtel: " + e.getMessage());
            e.printStackTrace();
            throw new HotelBookingException("Erreur lors de la réservation: " + e.getMessage());
        }
    }
    
    /**
     * 🔍 Vérifier qu'une réservation de vol existe et correspond
     */
    private boolean verifyFlightReservation(
            String customerId, 
            String flightReservationId,
            String hotelCity) {
        
        try {
            Reservation flightRes = reservationManager.getReservation(flightReservationId);
            
            if (!flightRes.customerId.equals(customerId)) {
                System.out.println("⚠️ Réservation de vol ne correspond pas au client");
                return false;
            }
            
            if (!"CONFIRMED".equals(flightRes.status)) {
                System.out.println("⚠️ Réservation de vol non confirmée");
                return false;
            }
            
            System.out.println("✓ Réservation de vol valide pour Dynamic Packaging");
            return true;
            
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de vérifier la réservation de vol: " + 
                             e.getMessage());
            return false;
        }
    }
    
    @Override
    public HotelReservation[] getCustomerHotelReservations(String customerId) {
        List<Document> docs = hotelReservationRepository.findByCustomerId(customerId);
        
        HotelReservation[] reservations = new HotelReservation[docs.size()];
        for (int i = 0; i < docs.size(); i++) {
            reservations[i] = ManagerHelper.documentToHotelReservation(docs.get(i));
        }
        
        System.out.println("✅ " + reservations.length + " réservation(s) hôtel trouvée(s)");
        return reservations;
    }
    
    @Override
    public HotelReservation getHotelReservation(String hotelReservationId) 
            throws HotelBookingException {
        
        Document doc = hotelReservationRepository.findById(hotelReservationId);
        if (doc == null) {
            throw new HotelBookingException(
                "Réservation d'hôtel non trouvée: " + hotelReservationId
            );
        }
        return ManagerHelper.documentToHotelReservation(doc);
    }
    
    /**
     * ✅ NOUVELLE MÉTHODE: Calculer le montant du remboursement selon la politique
     */
    public double calculateHotelRefundAmount(String hotelReservationId) 
            throws HotelBookingException {
        
        System.out.println("💰 Calcul remboursement hôtel: " + hotelReservationId);
        
        Document reservation = hotelReservationRepository.findById(hotelReservationId);
        if (reservation == null) {
            throw new HotelBookingException("Réservation introuvable");
        }
        
        String checkInDate = reservation.getString("checkInDate");
        double finalPrice = reservation.getDouble("finalPrice");
        
        // Calculer les heures restantes avant check-in
        long hoursRemaining = DateUtils.calculateHoursRemaining(checkInDate, "14:00");
        
        System.out.println("  Heures avant check-in: " + hoursRemaining);
        System.out.println("  Prix payé: " + finalPrice + " DZD");
        
        double refundAmount;
        double refundPercentage;
        
        if (hoursRemaining >= HOURS_FREE_CANCELLATION) {
            // 48h+ avant = 100% remboursement
            refundAmount = finalPrice;
            refundPercentage = 100.0;
            System.out.println("  ✅ Annulation gratuite (48h+): 100% remboursement");
            
        } else if (hoursRemaining >= HOURS_PARTIAL_REFUND) {
            // 24-48h avant = 50% remboursement
            refundAmount = finalPrice * (PARTIAL_REFUND_PERCENTAGE / 100);
            refundPercentage = PARTIAL_REFUND_PERCENTAGE;
            System.out.println("  ⚠️ Annulation tardive (24-48h): " + refundPercentage + "% remboursement");
            
        } else if (hoursRemaining >= 0) {
            // Moins de 24h = pas de remboursement
            refundAmount = 0.0;
            refundPercentage = 0.0;
            System.out.println("  ❌ Annulation très tardive (<24h): 0% remboursement");
            
        } else {
            // Check-in passé
            throw new HotelBookingException("Impossible d'annuler après le check-in");
        }
        
        System.out.println("  💵 Montant remboursable: " + refundAmount + " DZD");
        
        return refundAmount;
    }
    
    /**
     * ✅ MÉTHODE COMPLÈTE: Annuler une réservation d'hôtel avec remboursement
     */
    @Override
    public boolean cancelHotelReservation(String hotelReservationId) 
            throws HotelBookingException {
        
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  ❌ ANNULATION RÉSERVATION HÔTEL                  ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        
        try {
            Document reservation = hotelReservationRepository.findById(hotelReservationId);
            if (reservation == null) {
                throw new HotelBookingException("Réservation non trouvée");
            }
            
            String status = reservation.getString("status");
            System.out.println("  Statut actuel: " + status);
            
            if ("CANCELLED".equals(status)) {
                throw new HotelBookingException("Réservation déjà annulée");
            }
            
            if ("REFUNDED".equals(status)) {
                throw new HotelBookingException("Réservation déjà remboursée");
            }
            
            // Calculer le remboursement selon la politique
            double refundAmount = calculateHotelRefundAmount(hotelReservationId);
            
            // Mettre à jour le statut
            boolean updated = hotelReservationRepository.updateStatus(
                hotelReservationId, "CANCELLED"
            );
            
            if (!updated) {
                throw new HotelBookingException("Échec mise à jour statut");
            }
            
            // Remettre les chambres disponibles SEULEMENT si déjà confirmée
            if ("CONFIRMED".equals(status)) {
                String hotelId = reservation.getString("hotelId");
                int numberOfRooms = reservation.getInteger("numberOfRooms");
                hotelRepository.incrementAvailableRooms(hotelId, numberOfRooms);
                System.out.println("  ✅ " + numberOfRooms + " chambre(s) libérée(s)");
            }
            
            // ✅ CRÉER LE REMBOURSEMENT
            if (refundAmount > 0) {
                createHotelRefund(hotelReservationId, reservation, refundAmount);
            }
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  ✅ RÉSERVATION HÔTEL ANNULÉE                     ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  Réservation: " + hotelReservationId);
            System.out.println("║  Remboursement: " + refundAmount + " DZD");
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
            return true;
            
        } catch (HotelBookingException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur annulation: " + e.getMessage());
            e.printStackTrace();
            throw new HotelBookingException("Erreur annulation: " + e.getMessage());
        }
    }
    
    /**
     * ✅ NOUVELLE MÉTHODE: Créer un document de remboursement pour l'hôtel
     */
    private void createHotelRefund(String hotelReservationId, Document reservation, double amount) {
        String refundId = TokenManager.generateUniqueId("REF");
        String customerId = reservation.getString("customerId");
        
        Document refundDoc = new Document()
            .append("refundId", refundId)
            .append("reservationId", hotelReservationId)
            .append("reservationType", "HOTEL") // ⚠️ Important pour différencier
            .append("customerId", customerId)
            .append("amount", amount)
            .append("originalAmount", reservation.getDouble("finalPrice"))
            .append("status", "PENDING")
            .append("reason", "Customer cancellation")
            .append("refundDate", DateUtils.getCurrentDateTime())
            .append("hotelName", reservation.getString("hotelName"))
            .append("checkInDate", reservation.getString("checkInDate"));
        
        refundRepository.insertRefund(refundDoc);
        
        System.out.println("  💰 Remboursement créé: " + refundId + " (" + amount + " DZD)");
    }
    
    /**
     * ✅ NOUVELLE MÉTHODE: Annuler automatiquement les hôtels liés à un vol
     * Appelée quand un vol est annulé
     */
    public void cancelHotelsLinkedToFlight(String flightReservationId) {
        System.out.println("🔗 Recherche hôtels liés au vol: " + flightReservationId);
        
        try {
            List<Document> linkedHotels = hotelReservationRepository
                .findByFlightReservation(flightReservationId);
            
            if (linkedHotels.isEmpty()) {
                System.out.println("  ℹ️ Aucun hôtel lié à ce vol");
                return;
            }
            
            System.out.println("  🏨 " + linkedHotels.size() + " hôtel(s) lié(s) trouvé(s)");
            
            for (Document hotelReservation : linkedHotels) {
                String hotelResId = hotelReservation.getString("hotelReservationId");
                String status = hotelReservation.getString("status");
                
                // Annuler seulement si confirmé ou en attente
                if ("CONFIRMED".equals(status) || "PENDING_PAYMENT".equals(status)) {
                    try {
                        System.out.println("  → Annulation hôtel: " + hotelResId);
                        cancelHotelReservation(hotelResId);
                    } catch (Exception e) {
                        System.err.println("  ⚠️ Échec annulation hôtel " + hotelResId + ": " + e.getMessage());
                    }
                }
            }
            
            System.out.println("  ✅ Annulation hôtels liés terminée");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur annulation hôtels liés: " + e.getMessage());
        }
    }
    
    @Override
    public boolean checkAvailability(
            String hotelId,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms) {
        
        Document hotel = hotelRepository.findById(hotelId);
        if (hotel == null) return false;
        
        int availableRooms = hotel.getInteger("availableRooms", 0);
        return availableRooms >= numberOfRooms;
    }
    
    /**
     * ✅ MÉTHODE: Confirmer une réservation après paiement réussi
     */
    public boolean confirmHotelReservation(String hotelReservationId) {
        try {
            Document reservation = hotelReservationRepository.findById(hotelReservationId);
            if (reservation == null) {
                System.err.println("❌ Réservation introuvable: " + hotelReservationId);
                return false;
            }
            
            String currentStatus = reservation.getString("status");
            if (!"PENDING_PAYMENT".equals(currentStatus)) {
                System.err.println("⚠️ Réservation pas en attente: " + currentStatus);
                return false;
            }
            
            String hotelId = reservation.getString("hotelId");
            int numberOfRooms = reservation.getInteger("numberOfRooms");
            boolean roomsDecremented = hotelRepository.decrementAvailableRooms(hotelId, numberOfRooms);
            
            if (!roomsDecremented) {
                System.err.println("❌ Échec décrémentation chambres (concurrence)");
                return false;
            }
            
            boolean updated = hotelReservationRepository.updateStatus(
                hotelReservationId, "CONFIRMED"
            );
            
            if (updated) {
                System.out.println("✅ Réservation hôtel confirmée après paiement: " + hotelReservationId);
                return true;
            }
            
            hotelRepository.incrementAvailableRooms(hotelId, numberOfRooms);
            return false;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur confirmation réservation: " + e.getMessage());
            return false;
        }
    }
}