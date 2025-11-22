// src/main/java/com/skybooking/rest/handlers/download/DownloadSingleTicketHandler.java

package com.skybooking.rest.handlers.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.pdf.TicketPDFGenerator;
import FlightReservation.*;
import java.io.IOException;

/**
 * 🎫 Handler pour télécharger UN SEUL ticket en PDF
 * Version optimisée avec récupération automatique de la réservation
 * 
 * Fonctionnalités:
 * - Récupération directe du ticket par ID
 * - Récupération automatique de la réservation associée
 * - Génération de PDF individuel avec QR code
 * - Gestion complète des erreurs
 */
public class DownloadSingleTicketHandler implements HttpHandler {
    
    private final ReservationManager reservationManager;
    private final FlightManager flightManager;
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public DownloadSingleTicketHandler(ReservationManager reservationManager,
                                      FlightManager flightManager,
                                      CustomerManager customerManager,
                                      TimeoutExecutor timeoutExecutor) {
        this.reservationManager = reservationManager;
        this.flightManager = flightManager;
        this.customerManager = customerManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Configuration CORS
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "GET")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            // Extraire ticketId depuis l'URL: /api/download/ticket/{ticketId}
            String ticketId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (ticketId == null || ticketId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de ticket invalide");
                return;
            }
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  🎫 TÉLÉCHARGEMENT TICKET INDIVIDUEL               ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  Ticket ID: " + ticketId);
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
            // ==================== ÉTAPE 1: RÉCUPÉRER LE TICKET ====================
            System.out.println("→ Étape 1/4: Récupération du ticket...");
            
            Ticket targetTicket = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getTicketById(ticketId);
            }, 10, "récupération ticket");
            
            if (targetTicket == null) {
                System.err.println("❌ Ticket non trouvé");
                ResponseHelper.sendError(exchange, 404, "Ticket non trouvé : " + ticketId);
                return;
            }
            
            System.out.println("✅ Ticket récupéré:");
            System.out.println("   - Passager: " + targetTicket.passengerName);
            System.out.println("   - Siège: " + targetTicket.seatNumber);
            System.out.println("   - Vol: " + targetTicket.flightNumber);
            
            // ==================== ÉTAPE 2: RÉCUPÉRER LA RÉSERVATION ====================
            System.out.println("→ Étape 2/4: Récupération de la réservation...");
            
            Reservation reservation = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getReservationByTicketId(ticketId);
            }, 10, "récupération réservation");
            
            if (reservation == null) {
                System.err.println("❌ Réservation non trouvée");
                ResponseHelper.sendError(exchange, 404, "Réservation non trouvée pour ce ticket");
                return;
            }
            
            System.out.println("✅ Réservation récupérée: " + reservation.reservationId);
            
            // ==================== ÉTAPE 3: RÉCUPÉRER VOL ET CLIENT ====================
            System.out.println("→ Étape 3/4: Récupération des détails (vol + client)...");
            
            Flight flight = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.getFlightById(reservation.flightId);
            }, 10, "récupération vol");
            
            if (flight == null) {
                System.err.println("❌ Vol non trouvé");
                ResponseHelper.sendError(exchange, 404, "Vol non trouvé");
                return;
            }
            
            Customer customer = timeoutExecutor.executeWithTimeout(() -> {
                return customerManager.getCustomerById(reservation.customerId);
            }, 10, "récupération client");
            
            if (customer == null) {
                System.err.println("❌ Client non trouvé");
                ResponseHelper.sendError(exchange, 404, "Client non trouvé");
                return;
            }
            
            System.out.println("✅ Vol: " + flight.flightNumber + " (" + flight.airline + ")");
            System.out.println("✅ Client: " + customer.firstName + " " + customer.lastName);
            
            // ==================== ÉTAPE 4: GÉNÉRER LE PDF ====================
            System.out.println("→ Étape 4/4: Génération du PDF individuel...");
            
            byte[] pdfBytes = TicketPDFGenerator.generateSingleTicket(
                reservation, 
                targetTicket, 
                flight, 
                customer
            );
            
            if (pdfBytes == null || pdfBytes.length == 0) {
                System.err.println("❌ PDF vide ou null");
                ResponseHelper.sendError(exchange, 500, "Erreur génération PDF");
                return;
            }
            
            System.out.println("✅ PDF généré: " + pdfBytes.length + " bytes");
            
            // ==================== ÉTAPE 5: ENVOYER LE PDF ====================
            String filename = String.format("ticket_%s_%s.pdf", 
                targetTicket.passengerName.replace(" ", "_").replace(".", ""), 
                targetTicket.seatNumber.replace(" ", ""));
            
            ResponseHelper.sendBinaryResponse(
                exchange, 
                pdfBytes, 
                "application/pdf", 
                filename
            );
            
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║  ✅ TÉLÉCHARGEMENT RÉUSSI                          ║");
            System.out.println("╠═══════════════════════════════════════════════════╣");
            System.out.println("║  Fichier: " + filename);
            System.out.println("║  Taille: " + pdfBytes.length + " bytes");
            System.out.println("║  Passager: " + targetTicket.passengerName);
            System.out.println("║  Siège: " + targetTicket.seatNumber);
            System.out.println("╚═══════════════════════════════════════════════════╝");
            
        } catch (ReservationException e) {
            System.err.println("❌ Erreur CORBA: " + e.message);
            ResponseHelper.sendError(exchange, 404, e.message);
            
        } catch (FlightNotFoundException e) {
            System.err.println("❌ Vol non trouvé: " + e.message);
            ResponseHelper.sendError(exchange, 404, e.message);
            
        } catch (Exception e) {
            System.err.println("╔═══════════════════════════════════════════════════╗");
            System.err.println("║  ❌ ERREUR CRITIQUE                                ║");
            System.err.println("╠═══════════════════════════════════════════════════╣");
            System.err.println("║  Type: " + e.getClass().getSimpleName());
            System.err.println("║  Message: " + e.getMessage());
            System.err.println("╚═══════════════════════════════════════════════════╝");
            e.printStackTrace();
            
            ResponseHelper.sendError(exchange, 500, 
                "Erreur lors de la génération du PDF: " + e.getMessage());
        }
    }
}