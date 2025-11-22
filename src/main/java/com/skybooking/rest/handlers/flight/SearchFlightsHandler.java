// src/main/java/com/skybooking/rest/handlers/flight/SearchFlightsHandler.java

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
import java.util.Map;

/**
 * 🔍 Handler pour la recherche de vols
 */
public class SearchFlightsHandler implements HttpHandler {
    
    private final FlightManager flightManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public SearchFlightsHandler(FlightManager flightManager,
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
            System.out.println("→ Requête SEARCH FLIGHTS reçue");
            
            // Extraire les paramètres
            Map<String, String> params = RequestHelper.parseQueryParams(exchange.getRequestURI());
            String from = params.getOrDefault("from", "");
            String to = params.getOrDefault("to", "");
            String date = params.getOrDefault("date", "");
            String seatClass = params.getOrDefault("seatClass", "ECONOMY");
            
            System.out.println("  Départ: " + from);
            System.out.println("  Arrivée: " + to);
            System.out.println("  Date: " + date);
            System.out.println("  Classe: " + seatClass);
            
            // Appel CORBA avec timeout
            Flight[] flights = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.searchFlights(from, to, date, seatClass);
            }, Constants.TIMEOUT_SEARCH, "recherche de vols");
            
            // Convertir en JSON
            JsonArray jsonFlights = new JsonArray();
            for (Flight flight : flights) {
                jsonFlights.add(JsonHelper.flightToJson(flight));
            }
            
            System.out.println("✅ " + flights.length + " vol(s) retourné(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, jsonFlights);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur recherche: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}