// src/main/java/com/skybooking/managers/impl/CancellationManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.*;
import com.skybooking.security.TokenManager;
import com.skybooking.utils.Constants;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * ❌ IMPLÉMENTATION DU GESTIONNAIRE D'ANNULATION
 * Politique d'annulation, remboursements et modifications
 */
public class CancellationManagerImpl extends CancellationManagerPOA {
    
    private final ReservationRepository reservationRepository;
    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final CancellationRepository cancellationRepository;
    private final RefundRepository refundRepository;
    
    // ✅ NOUVEAU: Référence au HotelManager pour annulations liées
    private HotelManagerImpl hotelManager;
    
    public CancellationManagerImpl() {
        this.reservationRepository = new ReservationRepository();
        this.flightRepository = new FlightRepository();
        this.seatRepository = new SeatRepository();
        this.cancellationRepository = new CancellationRepository();
        this.refundRepository = new RefundRepository();
        System.out.println("✅ CancellationManager initialisé");
    }
    
    /**
     * ✅ NOUVEAU: Setter pour injection du HotelManager
     */
    public void setHotelManager(HotelManagerImpl hotelManager) {
        this.hotelManager = hotelManager;
        System.out.println("✅ HotelManager injecté dans CancellationManager");
    }
    
    @Override
    public CancellationPolicy getCancellationPolicy(String reservationId) {
        System.out.println("→ Récupération politique pour: " + reservationId);
        
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) return null;
        
        Document flightDoc = flightRepository.findById(resDoc.getString("flightId"));
        if (flightDoc == null) return null;
        
        long hoursRemaining = DateUtils.calculateHoursRemaining(
            flightDoc.getString("departureDate"),
            flightDoc.getString("departureTime")
        );
        
        if (hoursRemaining >= Constants.HOURS_FREE_CANCELLATION) {
            return new CancellationPolicy(
                (int) Constants.HOURS_FREE_CANCELLATION,
                Constants.FREE_CANCELLATION_REFUND,
                0.0
            );
        } else if (hoursRemaining >= Constants.HOURS_PARTIAL_REFUND) {
            return new CancellationPolicy(
                (int) Constants.HOURS_PARTIAL_REFUND,
                Constants.PARTIAL_REFUND_PERCENTAGE,
                Constants.LATE_CANCELLATION_FEE
            );
        } else {
            return new CancellationPolicy(
                0,
                0.0,
                resDoc.getDouble("totalPrice")
            );
        }
    }
    
    @Override
    public double calculateRefundAmount(String reservationId) {
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) return 0.0;
        
        Document flightDoc = flightRepository.findById(resDoc.getString("flightId"));
        if (flightDoc == null) return 0.0;
        
        double totalPrice = resDoc.getDouble("totalPrice");
        long hoursRemaining = DateUtils.calculateHoursRemaining(
            flightDoc.getString("departureDate"),
            flightDoc.getString("departureTime")
        );
        
        if (hoursRemaining >= Constants.HOURS_FREE_CANCELLATION) {
            return totalPrice;
        } else if (hoursRemaining >= Constants.HOURS_PARTIAL_REFUND) {
            return (totalPrice * Constants.PARTIAL_REFUND_PERCENTAGE) - 
                   Constants.LATE_CANCELLATION_FEE;
        } else {
            return 0.0;
        }
    }
    
    /**
     * ✅ MODIFIÉ: Annulation avec support cascade hôtels
     */
    @Override
    public boolean cancelReservation(String reservationId, String reason)
            throws CancellationNotAllowedException {
        
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  ❌ ANNULATION RÉSERVATION VOL                    ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println("  Réservation: " + reservationId);
        System.out.println("  Raison: " + reason);
        
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) {
            throw new CancellationNotAllowedException("Réservation introuvable", 0);
        }
        
        String status = resDoc.getString("status");
        if ("CANCELLED".equals(status) || "REFUNDED".equals(status)) {
            throw new CancellationNotAllowedException(
                "Cette réservation est déjà annulée", 0
            );
        }
        
        Document flightDoc = flightRepository.findById(resDoc.getString("flightId"));
        if (flightDoc == null) {
            throw new CancellationNotAllowedException("Vol introuvable", 0);
        }
        
        long hoursRemaining = DateUtils.calculateHoursRemaining(
            flightDoc.getString("departureDate"),
            flightDoc.getString("departureTime")
        );
        
        if (hoursRemaining < 0) {
            throw new CancellationNotAllowedException("Le vol est déjà parti", 0);
        }
        
        if (hoursRemaining < Constants.HOURS_PARTIAL_REFUND) {
            throw new CancellationNotAllowedException(
                "Annulation impossible moins de " + Constants.HOURS_PARTIAL_REFUND + 
                "h avant le départ",
                (int) hoursRemaining
            );
        }
        
        double refundAmount = calculateRefundAmount(reservationId);
        
        @SuppressWarnings("unchecked")
        List<String> seatNumbers = (List<String>) resDoc.get("seatNumbers");
        String flightId = resDoc.getString("flightId");
        
        for (String seatNumber : seatNumbers) {
            seatRepository.updateSeatStatus(flightId, seatNumber, "AVAILABLE");
            flightRepository.incrementAvailableSeats(flightId);
        }
        
        reservationRepository.updateStatus(reservationId, "CANCELLED");
        
        Document cancellationDoc = new Document()
            .append("reservationId", reservationId)
            .append("reservationType", "FLIGHT") // ✅ NOUVEAU
            .append("reason", reason)
            .append("refundAmount", refundAmount)
            .append("hoursBeforeDeparture", hoursRemaining)
            .append("cancellationDate", DateUtils.getCurrentDateTime());
        
        cancellationRepository.insertCancellation(cancellationDoc);
        
        createRefund(reservationId, "FLIGHT", refundAmount, reason);
        
        // ✅ NOUVEAU: Annuler automatiquement les hôtels liés
        if (hotelManager != null) {
            System.out.println("🔗 Vérification hôtels liés au vol...");
            try {
                hotelManager.cancelHotelsLinkedToFlight(reservationId);
            } catch (Exception e) {
                System.err.println("⚠️ Erreur annulation hôtels liés: " + e.getMessage());
                // Ne pas bloquer l'annulation du vol si échec hôtels
            }
        }
        
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  ✅ RÉSERVATION VOL ANNULÉE                       ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  Remboursement vol: " + refundAmount + " DZD");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        
        return true;
    }
    
    @Override
    public boolean canModifyReservation(String reservationId) {
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) return false;
        
        String status = resDoc.getString("status");
        if (!"CONFIRMED".equals(status)) return false;
        
        Document flightDoc = flightRepository.findById(resDoc.getString("flightId"));
        if (flightDoc == null) return false;
        
        long hoursRemaining = DateUtils.calculateHoursRemaining(
            flightDoc.getString("departureDate"),
            flightDoc.getString("departureTime")
        );
        
        return hoursRemaining >= 24;
    }
    
    @Override
    public boolean modifySeats(String reservationId, String[] newSeats)
            throws ModificationNotAllowedException, SeatNotAvailableException {
        
        System.out.println("→ Modification sièges: " + reservationId);
        
        if (!canModifyReservation(reservationId)) {
            throw new ModificationNotAllowedException(
                "Modification impossible moins de 24h avant le départ"
            );
        }
        
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) {
            throw new ModificationNotAllowedException("Réservation introuvable");
        }
        
        String flightId = resDoc.getString("flightId");
        
        @SuppressWarnings("unchecked")
        List<String> oldSeats = (List<String>) resDoc.get("seatNumbers");
        
        if (newSeats.length != oldSeats.size()) {
            throw new ModificationNotAllowedException(
                "Le nombre de sièges doit rester identique (" + oldSeats.size() + ")"
            );
        }
        
        List<Document> seatsToReserve = new ArrayList<>();
        double totalPrice = 0;
        
        for (String seatNumber : newSeats) {
            Document seatDoc = seatRepository.findSeat(flightId, seatNumber);
            
            if (seatDoc == null) {
                throw new SeatNotAvailableException("Siège inexistant: " + seatNumber);
            }
            
            String status = seatDoc.getString("status");
            if (!"AVAILABLE".equals(status) && !oldSeats.contains(seatNumber)) {
                throw new SeatNotAvailableException("Siège non disponible: " + seatNumber);
            }
            
            seatsToReserve.add(seatDoc);
            totalPrice += seatDoc.getDouble("price");
        }
        
        for (String oldSeat : oldSeats) {
            if (!Arrays.asList(newSeats).contains(oldSeat)) {
                seatRepository.updateSeatStatus(flightId, oldSeat, "AVAILABLE");
                System.out.println("  ✅ Siège libéré: " + oldSeat);
            }
        }
        
        for (String newSeat : newSeats) {
            if (!oldSeats.contains(newSeat)) {
                if (!seatRepository.updateSeatStatusAtomic(
                        flightId, newSeat, "AVAILABLE", "OCCUPIED")) {
                    for (String rolledSeat : oldSeats) {
                        if (!Arrays.asList(newSeats).contains(rolledSeat)) {
                            seatRepository.updateSeatStatus(
                                flightId, rolledSeat, "OCCUPIED"
                            );
                        }
                    }
                    throw new SeatNotAvailableException(
                        "Échec réservation siège: " + newSeat
                    );
                }
                System.out.println("  ✅ Siège réservé: " + newSeat);
            }
        }
        
        Document updates = new Document()
            .append("seatNumbers", Arrays.asList(newSeats))
            .append("totalPrice", totalPrice);
        
        reservationRepository.updateReservation(reservationId, updates);
        
        System.out.println("✅ Sièges modifiés avec succès");
        System.out.println("  Ancien(s): " + String.join(", ", oldSeats));
        System.out.println("  Nouveau(x): " + String.join(", ", newSeats));
        System.out.println("  Nouveau total: " + totalPrice + " DZD");
        
        return true;
    }
    
    @Override
    public Flight[] getAlternativeFlights(String reservationId) {
        System.out.println("→ Recherche vols alternatifs pour: " + reservationId);
        
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) return new Flight[0];
        
        Document currentFlight = flightRepository.findById(resDoc.getString("flightId"));
        if (currentFlight == null) return new Flight[0];
        
        String departureCity = currentFlight.getString("departureCity");
        String arrivalCity = currentFlight.getString("arrivalCity");
        String departureDate = currentFlight.getString("departureDate");
        
        List<Document> alternativeFlightDocs = flightRepository.searchSimilarFlights(
            departureCity,
            arrivalCity,
            departureDate,
            resDoc.getString("flightId")
        );
        
        Flight[] alternatives = new Flight[alternativeFlightDocs.size()];
        for (int i = 0; i < alternativeFlightDocs.size(); i++) {
            alternatives[i] = documentToFlight(alternativeFlightDocs.get(i));
        }
        
        System.out.println("  ✅ " + alternatives.length + " vol(s) alternatif(s) trouvé(s)");
        return alternatives;
    }
    
    @Override
    public boolean changeFlight(String reservationId, String newFlightId)
            throws ModificationNotAllowedException {
        
        System.out.println("→ Changement de vol: " + reservationId + " → " + newFlightId);
        
        if (!canModifyReservation(reservationId)) {
            throw new ModificationNotAllowedException(
                "Modification impossible moins de 24h avant le départ"
            );
        }
        
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) {
            throw new ModificationNotAllowedException("Réservation introuvable");
        }
        
        String oldFlightId = resDoc.getString("flightId");
        if (oldFlightId.equals(newFlightId)) {
            throw new ModificationNotAllowedException(
                "Nouveau vol identique au vol actuel"
            );
        }
        
        Document newFlight = flightRepository.findById(newFlightId);
        if (newFlight == null) {
            throw new ModificationNotAllowedException("Nouveau vol introuvable");
        }
        
        @SuppressWarnings("unchecked")
        List<String> seatNumbers = (List<String>) resDoc.get("seatNumbers");
        int requiredSeats = seatNumbers.size();
        
        if (newFlight.getInteger("availableSeats") < requiredSeats) {
            throw new ModificationNotAllowedException(
                "Pas assez de sièges disponibles sur le nouveau vol"
            );
        }
        
        for (String seatNumber : seatNumbers) {
            seatRepository.updateSeatStatus(oldFlightId, seatNumber, "AVAILABLE");
            flightRepository.incrementAvailableSeats(oldFlightId);
        }
        
        List<Document> newSeatsAvailable = seatRepository.getAvailableSeats(
            newFlightId, 
            requiredSeats
        );
        
        if (newSeatsAvailable.size() < requiredSeats) {
            for (String seatNumber : seatNumbers) {
                seatRepository.updateSeatStatus(oldFlightId, seatNumber, "OCCUPIED");
                flightRepository.decrementAvailableSeats(oldFlightId);
            }
            throw new ModificationNotAllowedException(
                "Échec lors de la réservation des nouveaux sièges"
            );
        }
        
        List<String> newSeatNumbers = new ArrayList<>();
        double newTotalPrice = 0;
        
        for (Document seatDoc : newSeatsAvailable) {
            String seatNumber = seatDoc.getString("seatNumber");
            seatRepository.updateSeatStatus(newFlightId, seatNumber, "OCCUPIED");
            flightRepository.decrementAvailableSeats(newFlightId);
            newSeatNumbers.add(seatNumber);
            newTotalPrice += seatDoc.getDouble("price");
        }
        
        Document updates = new Document()
            .append("flightId", newFlightId)
            .append("seatNumbers", newSeatNumbers)
            .append("totalPrice", newTotalPrice)
            .append("oldFlightId", oldFlightId);
        
        reservationRepository.updateReservation(reservationId, updates);
        
        System.out.println("✅ Vol changé avec succès");
        System.out.println("  Ancien vol: " + oldFlightId);
        System.out.println("  Nouveau vol: " + newFlightId);
        System.out.println("  Nouveaux sièges: " + String.join(", ", newSeatNumbers));
        System.out.println("  Nouveau total: " + newTotalPrice + " DZD");
        
        return true;
    }
    
    // ==================== MÉTHODES PRIVÉES ====================
    
    /**
     * ✅ MODIFIÉ: Créer un remboursement avec type (FLIGHT ou HOTEL)
     */
    private void createRefund(String reservationId, String type, double amount, String reason) {
        Document refundDoc = new Document()
            .append("refundId", TokenManager.generateUniqueId("REF"))
            .append("reservationId", reservationId)
            .append("reservationType", type) // ✅ NOUVEAU
            .append("amount", amount)
            .append("status", "PENDING")
            .append("reason", reason)
            .append("refundDate", DateUtils.getCurrentDateTime());
        
        refundRepository.insertRefund(refundDoc);
        
        System.out.println("  💰 Remboursement créé (" + type + "): " + amount + " DZD");
    }
    
    private Flight documentToFlight(Document doc) {
        return new Flight(
            doc.getString("flightId"),
            doc.getString("flightNumber"),
            doc.getString("airline"),
            doc.getString("departureCity"),
            doc.getString("arrivalCity"),
            doc.getString("departureDate"),
            doc.getString("departureTime"),
            doc.getString("arrivalDate"),
            doc.getString("arrivalTime"),
            doc.getString("duration"),
            doc.getDouble("economyPrice"),
            doc.getDouble("businessPrice"),
            doc.getDouble("firstClassPrice"),
            (int) doc.getInteger("availableSeats", 0),
            doc.getString("aircraftType")
        );
    }
}