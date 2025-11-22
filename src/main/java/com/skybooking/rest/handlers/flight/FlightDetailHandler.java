// src/main/java/com/skybooking/rest/handlers/flight/FlightDetailHandler.java

package com.skybooking.rest.handlers.flight;

import com.google.gson.JsonObject;
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
 * ✈️ Handler pour les détails d'un vol
 */
public class FlightDetailHandler implements HttpHandler {
    
    private final FlightManager flightManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public FlightDetailHandler(FlightManager flightManager,
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
            System.out.println("→ Requête FLIGHT DETAIL reçue");
            
            String flightId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 3
            );
            
            if (flightId == null || flightId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de vol invalide");
                return;
            }
            
            System.out.println("  Vol ID: " + flightId);
            
            // Appel CORBA avec timeout
            Flight flight = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.getFlightById(flightId);
            }, Constants.TIMEOUT_DEFAULT, "détails vol");
            
            JsonObject jsonFlight = JsonHelper.flightToJson(flight);
            
            System.out.println("✅ Détails du vol " + flightId + " retournés");
            ResponseHelper.sendJsonResponse(exchange, 200, jsonFlight);
            
        } catch (FlightNotFoundException e) {
            System.err.println("❌ Vol non trouvé: " + e.message);
            ResponseHelper.sendError(exchange, 404, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur détails vol: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
