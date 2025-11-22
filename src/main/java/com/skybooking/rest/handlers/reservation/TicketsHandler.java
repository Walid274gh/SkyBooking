// src/main/java/com/skybooking/rest/handlers/reservation/TicketsHandler.java

package com.skybooking.rest.handlers.reservation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 🎫 Handler pour récupérer les tickets d'une réservation
 */
public class TicketsHandler implements HttpHandler {
    
    private final ReservationManager reservationManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public TicketsHandler(ReservationManager reservationManager,
                         TimeoutExecutor timeoutExecutor) {
        this.reservationManager = reservationManager;
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
                exchange.getRequestURI().getPath(), 3
            );
            
            if (reservationId == null || reservationId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de réservation invalide");
                return;
            }
            
            System.out.println("→ Requête TICKETS reçue pour: " + reservationId);
            
            // Appel CORBA avec timeout
            Ticket[] tickets = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getTickets(reservationId);
            }, Constants.TIMEOUT_DEFAULT, "tickets");
            
            JsonArray jsonTickets = new JsonArray();
            for (Ticket ticket : tickets) {
                JsonObject jsonTicket = new JsonObject();
                jsonTicket.addProperty("ticketId", ticket.ticketId);
                jsonTicket.addProperty("reservationId", ticket.reservationId);
                jsonTicket.addProperty("passengerName", ticket.passengerName);
                jsonTicket.addProperty("seatNumber", ticket.seatNumber);
                jsonTicket.addProperty("flightNumber", ticket.flightNumber);
                jsonTicket.addProperty("departureCity", ticket.departureCity);
                jsonTicket.addProperty("arrivalCity", ticket.arrivalCity);
                jsonTicket.addProperty("departureDate", ticket.departureDate);
                jsonTicket.addProperty("departureTime", ticket.departureTime);
                jsonTicket.addProperty("price", ticket.price);
                jsonTickets.add(jsonTicket);
            }
            
            System.out.println("✅ " + tickets.length + " ticket(s) retourné(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, jsonTickets);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur tickets: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}