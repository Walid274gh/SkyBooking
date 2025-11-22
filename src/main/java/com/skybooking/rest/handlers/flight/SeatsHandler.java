// src/main/java/com/skybooking/rest/handlers/flight/SeatsHandler.java

package com.skybooking.rest.handlers.flight;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 💺 Handler pour les sièges disponibles
 */
public class SeatsHandler implements HttpHandler {
    
    private final FlightManager flightManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public SeatsHandler(FlightManager flightManager,
                       TimeoutExecutor timeoutExecutor) {
        this.flightManager = flightManager;
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
            System.out.println("→ Requête SEATS reçue");
            
            String flightId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 3
            );
            
            if (flightId == null || flightId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de vol invalide");
                return;
            }
            
            System.out.println("  Vol ID: " + flightId);
            
            // Appel CORBA avec timeout
            Seat[] seats = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.getAvailableSeats(flightId);
            }, Constants.TIMEOUT_DEFAULT, "sièges disponibles");
            
            JsonArray jsonSeats = new JsonArray();
            for (Seat seat : seats) {
                jsonSeats.add(JsonHelper.seatToJson(seat));
            }
            
            System.out.println("✅ " + seats.length + " siège(s) retourné(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, jsonSeats);
            
        } catch (FlightNotFoundException e) {
            System.err.println("❌ Vol non trouvé: " + e.message);
            ResponseHelper.sendError(exchange, 404, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur sièges: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}