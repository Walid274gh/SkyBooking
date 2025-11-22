// src/main/java/com/skybooking/rest/handlers/account/PopularDestinationsHandler.java

package com.skybooking.rest.handlers.account;

import FlightReservation.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.TimeoutExecutor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 🔥 Handler REST pour les destinations populaires
 * 
 * Route:
 * - GET /api/account/popular-destinations : Récupérer les destinations populaires
 */
public class PopularDestinationsHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson = new Gson();
    
    public PopularDestinationsHandler(AccountManager accountManager, TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS Headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:5173");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        
        String method = exchange.getRequestMethod();
        
        try {
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("GET".equals(method)) {
                handleGet(exchange);
            } else {
                sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur PopularDestinationsHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Erreur serveur: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/account/popular-destinations
     * Récupérer les destinations populaires
     */
    private void handleGet(HttpExchange exchange) throws Exception {
        System.out.println("→ GET /api/account/popular-destinations");
        
        // Appel CORBA avec timeout
        Destination[] destinations = timeoutExecutor.executeWithTimeout(() -> 
            accountManager.getPopularDestinations(),
            10, TimeUnit.SECONDS
        );
        
        System.out.println("✅ " + destinations.length + " destination(s) populaire(s) récupérée(s)");
        
        // Conversion en JSON
        List<Map<String, Object>> destinationsList = new ArrayList<>();
        for (Destination dest : destinations) {
            Map<String, Object> destMap = new HashMap<>();
            destMap.put("cityName", dest.cityName);
            destMap.put("countryCode", dest.countryCode);
            destMap.put("imageUrl", dest.imageUrl);
            destMap.put("flightCount", dest.flightCount);
            destMap.put("startingPrice", dest.startingPrice);
            destinationsList.add(destMap);
        }
        
        sendJsonResponse(exchange, 200, destinationsList);
    }
    
    /**
     * OPTIONS - Préflight CORS
     */
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }
    
    // ==================== MÉTHODES UTILITAIRES ====================
    
    /**
     * Envoyer une réponse JSON
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        
        String jsonResponse = gson.toJson(data);
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        
        exchange.sendResponseHeaders(statusCode, bytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    /**
     * Envoyer une erreur JSON
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        
        sendJsonResponse(exchange, statusCode, error);
    }
}