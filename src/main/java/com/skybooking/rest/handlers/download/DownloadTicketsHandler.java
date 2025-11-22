// src/main/java/com/skybooking/rest/handlers/download/DownloadTicketsHandler.java

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
 * 📥 Handler pour télécharger les tickets en PDF
 */
public class DownloadTicketsHandler implements HttpHandler {
    
    private final ReservationManager reservationManager;
    private final FlightManager flightManager;
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public DownloadTicketsHandler(ReservationManager reservationManager,
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
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "GET")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String reservationId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (reservationId == null || reservationId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de réservation invalide");
                return;
            }
            
            System.out.println("→ Requête DOWNLOAD TICKETS PDF: " + reservationId);
            
            // Récupérer les données via CORBA
            Reservation reservation = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getReservation(reservationId);
            }, 10, "récupération réservation");
            
            if (reservation == null) {
                ResponseHelper.sendError(exchange, 404, "Réservation non trouvée");
                return;
            }
            
            Ticket[] tickets = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getTickets(reservationId);
            }, 10, "récupération tickets");
            
            if (tickets == null || tickets.length == 0) {
                ResponseHelper.sendError(exchange, 404, "Aucun ticket trouvé");
                return;
            }
            
            Flight flight = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.getFlightById(reservation.flightId);
            }, 10, "récupération vol");
            
            Customer customer = timeoutExecutor.executeWithTimeout(() -> {
                return customerManager.getCustomerById(reservation.customerId);
            }, 10, "récupération client");
            
            // Générer le PDF
            System.out.println("→ Génération du PDF pour " + tickets.length + " ticket(s)...");
            byte[] pdfBytes = TicketPDFGenerator.generateReservationTickets(
                reservation, tickets, flight, customer
            );
            
            // Envoyer le PDF
            String filename = String.format("tickets_%s.pdf", reservation.reservationId);
            
            ResponseHelper.sendBinaryResponse(
                exchange, 
                pdfBytes, 
                "application/pdf", 
                filename
            );
            
            System.out.println("✅ PDF généré et envoyé: " + filename + 
                             " (" + pdfBytes.length + " bytes)");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur génération PDF: " + e.getMessage());
            e.printStackTrace();
            ResponseHelper.sendError(exchange, 500, 
                "Erreur lors de la génération du PDF: " + e.getMessage());
        }
    }
}