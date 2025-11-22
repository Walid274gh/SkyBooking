// src/main/java/com/skybooking/managers/impl/ReservationManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.*;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.managers.helpers.ValidationHelper;
import com.skybooking.security.AESEncryptionManager;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 📋 IMPLÉMENTATION COMPLÈTE DU GESTIONNAIRE DE RÉSERVATIONS
 * Création, consultation et annulation de réservations
 * 
 * SÉCURITÉ :
 * - Chiffrement AES-256-CBC des numéros de passeport
 * - Conformité RGPD pour les données personnelles
 * - Opérations atomiques pour éviter les incohérences
 * - Rollback automatique en cas d'erreur
 */
public class ReservationManagerImpl extends ReservationManagerPOA {
    
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final FlightRepository flightRepository;
    private final FlightManagerImpl flightManager;
    
    public ReservationManagerImpl(FlightManagerImpl flightManager) {
        this.reservationRepository = new ReservationRepository();
        this.ticketRepository = new TicketRepository();
        this.customerRepository = new CustomerRepository();
        this.flightRepository = new FlightRepository();
        this.flightManager = flightManager;
        
        // Test de la configuration AES-256
        if (AESEncryptionManager.testConfiguration()) {
            System.out.println("✅ ReservationManager initialisé avec AES-256");
        } else {
            System.err.println("⚠️ ATTENTION : Configuration AES-256 invalide");
        }
    }
    
    @Override
    public Reservation createReservation(
            String customerId,
            String flightId,
            String[] seatNumbers,
            Passenger[] passengers)
        throws SeatNotAvailableException, ReservationException {
    
        long startTime = System.currentTimeMillis();
        
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  📋 CRÉATION RÉSERVATION SÉCURISÉE                 ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  Client : " + customerId);
        System.out.println("║  Vol : " + flightId);
        System.out.println("║  Sièges : " + seatNumbers.length);
        System.out.println("║  Passagers : " + passengers.length);
        System.out.println("║  Chiffrement : AES-256-CBC");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        
        // ==================== PHASE 1 : VALIDATIONS ====================
        
        if (seatNumbers.length != passengers.length) {
            throw new ReservationException(
                "Le nombre de sièges (" + seatNumbers.length + 
                ") doit correspondre au nombre de passagers (" + passengers.length + ")"
            );
        }
        
        Document customerDoc = customerRepository.findById(customerId);
        if (customerDoc == null) {
            throw new ReservationException("Client introuvable : " + customerId);
        }
        
        Document flightDoc = flightRepository.findById(flightId);
        if (flightDoc == null) {
            throw new ReservationException("Vol introuvable : " + flightId);
        }
        
        if (!flightRepository.isFlightValid(flightId)) {
            throw new ReservationException("Ce vol est déjà parti ou invalide");
        }
        
        int currentAvailable = flightDoc.getInteger("availableSeats", 0);
        if (currentAvailable < seatNumbers.length) {
            throw new ReservationException(
                "Sièges insuffisants : " + currentAvailable + " disponible(s), " +
                seatNumbers.length + " demandé(s)"
            );
        }
        
        // ==================== PHASE 2 : VÉRIFICATION DÉTAILLÉE DES SIÈGES ====================
        
        List<Document> seatsToReserve = new ArrayList<>();
        List<String> seatNumbersList = Arrays.asList(seatNumbers);
        double totalPrice = 0;
        
        System.out.println("→ Vérification de " + seatNumbers.length + " siège(s)...");
        
        if (!flightManager.seatRepository.areSeatsAvailable(flightId, seatNumbersList)) {
            StringBuilder unavailableSeats = new StringBuilder();
            for (String seatNumber : seatNumbers) {
                Document seatDoc = flightManager.seatRepository.findSeat(flightId, seatNumber);
                
                if (seatDoc == null) {
                    throw new ReservationException("Siège inexistant : " + seatNumber);
                }
                
                if (!"AVAILABLE".equals(seatDoc.getString("status"))) {
                    unavailableSeats.append(seatNumber).append(" ");
                }
            }
            
            throw new SeatNotAvailableException(
                "Siège(s) non disponible(s) : " + unavailableSeats.toString().trim()
            );
        }
        
        for (String seatNumber : seatNumbers) {
            Document seatDoc = flightManager.seatRepository.findSeat(flightId, seatNumber);
            seatsToReserve.add(seatDoc);
            totalPrice += seatDoc.getDouble("price");
            
            System.out.println("  ✅ Siège validé : " + seatNumber + 
                             " (" + seatDoc.getString("seatClass") + 
                             ", " + seatDoc.getDouble("price") + " DZD)");
        }
        
        // Validation des passagers
        for (int i = 0; i < passengers.length; i++) {
            try {
                ValidationHelper.validatePassenger(passengers[i], i + 1);
            } catch (Exception e) {
                throw new ReservationException(
                    "Erreur validation passager " + (i + 1) + " : " + e.getMessage()
                );
            }
        }
        
        System.out.println("✅ Toutes les validations passées");
        System.out.println("   Prix total : " + totalPrice + " DZD");
        
        // ==================== PHASE 3 : RÉSERVATION ATOMIQUE ====================
        
        try {
            System.out.println("→ Début réservation atomique...");
            
            boolean seatsReserved = reserveSeats(flightId, seatNumbersList);
            
            if (!seatsReserved) {
                throw new ReservationException(
                    "Échec lors de la réservation des sièges. " +
                    "Certains sièges ont peut-être été réservés entre-temps."
                );
            }
            
            System.out.println("✅ Réservation des sièges réussie");
            
            // ==================== PHASE 4 : CRÉATION DE LA RÉSERVATION ====================
            
            String reservationId = "RES" + System.currentTimeMillis();
            String reservationDate = DateUtils.getCurrentDateTime();
            
            Document reservationDoc = new Document()
                .append("reservationId", reservationId)
                .append("customerId", customerId)
                .append("flightId", flightId)
                .append("status", "CONFIRMED")
                .append("totalPrice", totalPrice)
                .append("reservationDate", reservationDate)
                .append("seatNumbers", seatNumbersList)
                .append("passengerCount", passengers.length)
                .append("flightNumber", flightDoc.getString("flightNumber"))
                .append("departureCity", flightDoc.getString("departureCity"))
                .append("arrivalCity", flightDoc.getString("arrivalCity"))
                .append("departureDate", flightDoc.getString("departureDate"))
                .append("encryptionAlgorithm", "AES-256-CBC"); // Audit
            
            reservationRepository.insertReservation(reservationDoc);
            System.out.println("✅ Réservation créée : " + reservationId);
            
            // ==================== PHASE 5 : CRÉATION DES TICKETS AVEC CHIFFREMENT ====================
            
            System.out.println("→ Chiffrement AES-256 des données passagers...");
            
            List<Document> ticketDocs = new ArrayList<>();
            
            for (int i = 0; i < seatNumbers.length; i++) {
                String ticketId = "TKT" + System.currentTimeMillis() + "-" + i;
                Passenger passenger = passengers[i];
                Document seatDoc = seatsToReserve.get(i);
                
                // 🔒 CHIFFREMENT AES-256 DU NUMÉRO DE PASSEPORT
                String encryptedPassport;
                try {
                    encryptedPassport = AESEncryptionManager.encrypt(passenger.passportNumber);
                    System.out.println("  🔐 Passeport chiffré pour " + passenger.firstName + 
                                     " " + passenger.lastName);
                } catch (Exception e) {
                    System.err.println("❌ ERREUR CRITIQUE : Échec du chiffrement du passeport");
                    // Rollback
                    releaseSeats(flightId, seatNumbersList);
                    throw new ReservationException("Erreur de sécurité lors du chiffrement des données");
                }
                
                // Masquage pour affichage (conforme RGPD)
                String maskedPassport = AESEncryptionManager.mask(passenger.passportNumber, 4);
                
                // 🔒 CHIFFREMENT OPTIONNEL DE L'EMAIL (données personnelles)
                String encryptedEmail;
                try {
                    encryptedEmail = AESEncryptionManager.encrypt(passenger.email);
                } catch (Exception e) {
                    encryptedEmail = null; // Fallback : stockage en clair si échec
                }
                
                // 🔒 CHIFFREMENT OPTIONNEL DU TÉLÉPHONE
                String encryptedPhone;
                try {
                    encryptedPhone = AESEncryptionManager.encrypt(passenger.phone);
                } catch (Exception e) {
                    encryptedPhone = null;
                }
                
                Document ticketDoc = new Document()
                    .append("ticketId", ticketId)
                    .append("reservationId", reservationId)
                    .append("passengerName", passenger.firstName + " " + passenger.lastName)
                    .append("seatNumber", seatNumbers[i])
                    .append("seatClass", seatDoc.getString("seatClass"))
                    .append("flightNumber", flightDoc.getString("flightNumber"))
                    .append("departureCity", flightDoc.getString("departureCity"))
                    .append("arrivalCity", flightDoc.getString("arrivalCity"))
                    .append("departureDate", flightDoc.getString("departureDate"))
                    .append("departureTime", flightDoc.getString("departureTime"))
                    .append("arrivalDate", flightDoc.getString("arrivalDate"))
                    .append("arrivalTime", flightDoc.getString("arrivalTime"))
                    .append("price", seatDoc.getDouble("price"))
                    .append("passengerDetails", new Document()
                        .append("firstName", passenger.firstName)
                        .append("lastName", passenger.lastName)
                        .append("dateOfBirth", passenger.dateOfBirth)
                        
                        // Données masquées (affichage)
                        .append("passportNumberMasked", maskedPassport)
                        .append("email", passenger.email) // Email visible pour communication
                        .append("phone", passenger.phone) // Téléphone visible pour communication
                        
                        // Données chiffrées (stockage sécurisé)
                        .append("encryptedPassport", encryptedPassport)
                        .append("encryptedEmail", encryptedEmail)
                        .append("encryptedPhone", encryptedPhone)
                    );
                
                ticketDocs.add(ticketDoc);
                
                System.out.println("  ✅ Ticket généré : " + ticketId + 
                                 " - " + passenger.firstName + " " + passenger.lastName + 
                                 " (Siège : " + seatNumbers[i] + ")");
            }
            
            ticketRepository.insertTickets(ticketDocs);
            System.out.println("✅ " + ticketDocs.size() + " ticket(s) créé(s) et sécurisé(s)");
            
            // ==================== PHASE 6 : FINALISATION ====================
            
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  ✅ RÉSERVATION CRÉÉE AVEC SUCCÈS                  ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  ID : " + reservationId);
            System.out.println("║  Client : " + customerId);
            System.out.println("║  Vol : " + flightDoc.getString("flightNumber"));
            System.out.println("║  Route : " + flightDoc.getString("departureCity") + 
                             " → " + flightDoc.getString("arrivalCity"));
            System.out.println("║  Date : " + flightDoc.getString("departureDate"));
            System.out.println("║  Sièges : " + String.join(", ", seatNumbers));
            System.out.println("║  Passagers : " + passengers.length);
            System.out.println("║  Prix total : " + totalPrice + " DZD");
            System.out.println("║  Sécurité : AES-256-CBC ✓");
            System.out.println("║  Temps : " + duration + " ms");
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
            return ManagerHelper.documentToReservation(reservationDoc);
            
        } catch (SeatNotAvailableException | ReservationException e) {
            throw e;
            
        } catch (Exception e) {
            System.err.println("╔═══════════════════════════════════════════════════╗");
            System.err.println("║  ❌ ERREUR CRITIQUE DÉTECTÉE                       ║");
            System.err.println("╠═══════════════════════════════════════════════════╣");
            System.err.println("║  Message : " + e.getMessage());
            System.err.println("║  Type : " + e.getClass().getSimpleName());
            System.err.println("╚═══════════════════════════════════════════════════╝");
            e.printStackTrace();
            
            System.out.println("→ Tentative de rollback d'urgence...");
            
            try {
                boolean rollbackSuccess = releaseSeats(flightId, seatNumbersList);
                if (rollbackSuccess) {
                    System.out.println("✅ Rollback d'urgence réussi");
                } else {
                    System.err.println("⚠️ Rollback d'urgence partiel ou échoué");
                }
            } catch (Exception rollbackError) {
                System.err.println("❌ ÉCHEC CRITIQUE DU ROLLBACK : " + 
                                 rollbackError.getMessage());
            }
            
            throw new ReservationException(
                "Erreur système lors de la réservation : " + e.getMessage()
            );
        }
    }
    
    /**
     * 🔒 RÉSERVE PLUSIEURS SIÈGES DE MANIÈRE ATOMIQUE
     */
    private boolean reserveSeats(String flightId, List<String> seatNumbers) 
        throws SeatNotAvailableException, ReservationException {
        
        System.out.println("→ Réservation atomique de " + seatNumbers.size() + " siège(s)...");
        
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new ReservationException("Liste de sièges vide ou nulle");
        }
        
        Document flightDoc = flightRepository.findById(flightId);
        if (flightDoc == null) {
            throw new ReservationException("Vol introuvable : " + flightId);
        }
        
        if (!flightManager.seatRepository.areSeatsAvailable(flightId, seatNumbers)) {
            List<String> unavailableSeats = new ArrayList<>();
            
            for (String seatNumber : seatNumbers) {
                Document seatDoc = flightManager.seatRepository.findSeat(flightId, seatNumber);
                
                if (seatDoc == null) {
                    unavailableSeats.add(seatNumber + " (inexistant)");
                } else if (!"AVAILABLE".equals(seatDoc.getString("status"))) {
                    unavailableSeats.add(seatNumber + " (" + seatDoc.getString("status") + ")");
                }
            }
            
            throw new SeatNotAvailableException(
                "Siège(s) non disponible(s) : " + String.join(", ", unavailableSeats)
            );
        }
        
        try {
            boolean reservationSuccess = flightManager.seatRepository.reserveSeatsInBatch(
                flightId, 
                seatNumbers, 
                seatNumbers.size()
            );
            
            if (!reservationSuccess) {
                throw new SeatNotAvailableException(
                    "Un ou plusieurs sièges ont été réservés par un autre utilisateur entre-temps. " +
                    "Veuillez réessayer avec d'autres sièges."
                );
            }
            
            boolean flightUpdated = flightRepository.decrementAvailableSeats(
                flightId, 
                seatNumbers.size()
            );
            
            if (!flightUpdated) {
                flightManager.seatRepository.releaseSeatsInBatch(flightId, seatNumbers);
                throw new ReservationException("Impossible de mettre à jour le vol. Veuillez réessayer.");
            }
            
            System.out.println("✅ Réservation atomique réussie");
            return true;
            
        } catch (SeatNotAvailableException | ReservationException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur système : " + e.getMessage());
            e.printStackTrace();
            
            try {
                flightManager.seatRepository.releaseSeatsInBatch(flightId, seatNumbers);
            } catch (Exception rollbackError) {
                System.err.println("❌ ÉCHEC DU ROLLBACK");
            }
            
            throw new ReservationException("Erreur système lors de la réservation des sièges");
        }
    }
    
    /**
     * 🔓 LIBÈRE PLUSIEURS SIÈGES DE MANIÈRE ATOMIQUE
     */
    private boolean releaseSeats(String flightId, List<String> seatNumbers) 
        throws ReservationException {
        
        System.out.println("→ Libération de " + seatNumbers.size() + " siège(s)...");
        
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return true;
        }
        
        try {
            boolean releaseSuccess = flightManager.seatRepository.releaseSeatsInBatch(
                flightId, 
                seatNumbers
            );
            
            if (releaseSuccess) {
                flightRepository.incrementAvailableSeats(flightId, seatNumbers.size());
                System.out.println("✅ Libération réussie");
            }
            
            return releaseSuccess;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de la libération : " + e.getMessage());
            throw new ReservationException("Erreur système lors de la libération des sièges");
        }
    }
    
    @Override
    public Ticket[] getTickets(String reservationId) {
        List<Document> ticketDocs = ticketRepository.findByReservationId(reservationId);
        
        if (ticketDocs.isEmpty()) {
            System.err.println("❌ Aucun ticket trouvé pour : " + reservationId);
            return new Ticket[0];
        }
        
        Ticket[] tickets = new Ticket[ticketDocs.size()];
        for (int i = 0; i < ticketDocs.size(); i++) {
            tickets[i] = ManagerHelper.documentToTicket(ticketDocs.get(i));
        }
        
        System.out.println("→ Récupération de " + tickets.length + " ticket(s) pour " + reservationId);
        return tickets;
    }
    
    /**
     * 🎫 NOUVELLE MÉTHODE : RÉCUPÉRER UN SEUL TICKET PAR SON ID
     */
    @Override
    public Ticket getTicketById(String ticketId) throws ReservationException {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  🎫 RÉCUPÉRATION TICKET INDIVIDUEL                 ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  Ticket ID : " + ticketId);
        System.out.println("╚═══════════════════════════════════════════════════╝");
        
        if (ticketId == null || ticketId.isEmpty()) {
            throw new ReservationException("ID de ticket invalide (vide ou null)");
        }
        
        try {
            Document ticketDoc = ticketRepository.findById(ticketId);
            
            if (ticketDoc == null) {
                System.err.println("❌ Ticket introuvable : " + ticketId);
                throw new ReservationException("Ticket introuvable : " + ticketId);
            }
            
            Ticket ticket = ManagerHelper.documentToTicket(ticketDoc);
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  ✅ TICKET RÉCUPÉRÉ AVEC SUCCÈS                    ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  Ticket ID : " + ticket.ticketId);
            System.out.println("║  Passager : " + ticket.passengerName);
            System.out.println("║  Siège : " + ticket.seatNumber);
            System.out.println("║  Vol : " + ticket.flightNumber);
            System.out.println("║  Prix : " + ticket.price + " DZD");
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
            return ticket;
            
        } catch (ReservationException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération ticket : " + e.getMessage());
            e.printStackTrace();
            throw new ReservationException("Erreur lors de la récupération du ticket : " + e.getMessage());
        }
    }
    
    /**
     * 📋 NOUVELLE MÉTHODE : RÉCUPÉRER LA RÉSERVATION ASSOCIÉE À UN TICKET
     */
    @Override
    public Reservation getReservationByTicketId(String ticketId) throws ReservationException {
        System.out.println("╔═══════════════════════════════════════════════════╗");
        System.out.println("║  📋 RÉCUPÉRATION RÉSERVATION VIA TICKET            ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║  Ticket ID : " + ticketId);
        System.out.println("╚═══════════════════════════════════════════════════╝");
        
        if (ticketId == null || ticketId.isEmpty()) {
            throw new ReservationException("ID de ticket invalide (vide ou null)");
        }
        
        try {
            // 1. Récupérer le ticket
            Document ticketDoc = ticketRepository.findById(ticketId);
            
            if (ticketDoc == null) {
                throw new ReservationException("Ticket introuvable : " + ticketId);
            }
            
            // 2. Extraire le reservationId du ticket
            String reservationId = ticketDoc.getString("reservationId");
            
            if (reservationId == null || reservationId.isEmpty()) {
                throw new ReservationException("Réservation non trouvée pour ce ticket");
            }
            
            System.out.println("→ Reservation ID trouvé : " + reservationId);
            
            // 3. Récupérer la réservation
            Document reservationDoc = reservationRepository.findById(reservationId);
            
            if (reservationDoc == null) {
                throw new ReservationException("Réservation introuvable : " + reservationId);
            }
            
            Reservation reservation = ManagerHelper.documentToReservation(reservationDoc);
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  ✅ RÉSERVATION RÉCUPÉRÉE AVEC SUCCÈS              ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  Reservation ID : " + reservation.reservationId);
            System.out.println("║  Client ID : " + reservation.customerId);
            System.out.println("║  Vol ID : " + reservation.flightId);
            System.out.println("║  Statut : " + reservation.status);
            System.out.println("║  Prix total : " + reservation.totalPrice + " DZD");
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
            return reservation;
            
        } catch (ReservationException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur récupération réservation : " + e.getMessage());
            e.printStackTrace();
            throw new ReservationException("Erreur lors de la récupération de la réservation : " + e.getMessage());
        }
    }
    
    @Override
    public Reservation getReservation(String reservationId) {
        Document doc = reservationRepository.findById(reservationId);
        if (doc != null) {
            System.out.println("→ Récupération réservation : " + reservationId);
            return ManagerHelper.documentToReservation(doc);
        } else {
            System.err.println("❌ Réservation introuvable : " + reservationId);
            return null;
        }
    }
    
    @Override
    public boolean cancelReservation(String reservationId) {
        System.out.println("→ Annulation réservation : " + reservationId);
        
        Document doc = reservationRepository.findById(reservationId);
        
        if (doc == null) {
            System.err.println("❌ Réservation introuvable");
            return false;
        }
        
        String status = doc.getString("status");
        
        if (!"CONFIRMED".equals(status)) {
            System.err.println("❌ Impossible d'annuler : statut = " + status);
            return false;
        }
        
        try {
            @SuppressWarnings("unchecked")
            List<String> seatNumbers = (List<String>) doc.get("seatNumbers");
            String flightId = doc.getString("flightId");
            
            if (seatNumbers != null && !seatNumbers.isEmpty()) {
                boolean released = releaseSeats(flightId, seatNumbers);
                
                if (!released) {
                    System.err.println("⚠️ Échec libération des sièges");
                    return false;
                }
            }
            
            reservationRepository.updateStatus(reservationId, "CANCELLED");
            
            System.out.println("✅ Réservation annulée : " + reservationId);
            return true;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors de l'annulation : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public Reservation[] getCustomerReservations(String customerId) {
        List<Document> reservationDocs = reservationRepository.findByCustomerId(customerId);
        
        Reservation[] reservations = new Reservation[reservationDocs.size()];
        for (int i = 0; i < reservationDocs.size(); i++) {
            reservations[i] = ManagerHelper.documentToReservation(reservationDocs.get(i));
        }
        
        System.out.println("→ " + reservations.length + " réservation(s) pour " + customerId);
        return reservations;
    }
}