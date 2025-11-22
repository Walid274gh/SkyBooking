// src/main/java/com/skybooking/rest/handlers/account/FavoritesHandler.java

package com.skybooking.rest.handlers.account;

import FlightReservation.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.TimeoutExecutor;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 💙 Handler REST pour les destinations favorites
 * 
 * Routes:
 * - GET /api/account/favorites?customerId=xxx : Récupérer les favorites
 * - POST /api/account/favorites : Ajouter une destination favorite
 * - DELETE /api/account/favorites?customerId=xxx&cityName=yyy : Supprimer
 */
public class FavoritesHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson = new Gson();
    
    public FavoritesHandler(AccountManager accountManager, TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS Headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:5173");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
        
        String method = exchange.getRequestMethod();
        
        try {
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("GET".equals(method)) {
                handleGet(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur FavoritesHandler: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Erreur serveur: " + e.getMessage());
        }
    }
    
    /**
     * GET /api/account/favorites?customerId=xxx
     * Récupérer les destinations favorites d'un client
     */
    private void handleGet(HttpExchange exchange) throws Exception {
        System.out.println("→ GET /api/account/favorites");
        
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String customerId = params.get("customerId");
        
        if (customerId == null || customerId.isEmpty()) {
            sendError(exchange, 400, "Le paramètre customerId est requis");
            return;
        }
        
        // Appel CORBA avec timeout
        Destination[] favorites = timeoutExecutor.execute(() -> 
            accountManager.getFavoriteDestinations(customerId),
            10, TimeUnit.SECONDS
        );
        
        System.out.println("✅ " + favorites.length + " destination(s) favorite(s) récupérée(s)");
        
        // Conversion en JSON
        List<Map<String, Object>> favoritesList = new ArrayList<>();
        for (Destination dest : favorites) {
            Map<String, Object> destMap = new HashMap<>();
            destMap.put("cityName", dest.cityName);
            destMap.put("countryCode", dest.countryCode);
            destMap.put("imageUrl", dest.imageUrl);
            destMap.put("flightCount", dest.flightCount);
            destMap.put("startingPrice", dest.startingPrice);
            favoritesList.add(destMap);
        }
        
        sendJsonResponse(exchange, 200, favoritesList);
    }
    
    /**
     * POST /api/account/favorites
     * Ajouter une destination favorite
     * Body: { "customerId": "xxx", "cityName": "Paris" }
     */
    private void handlePost(HttpExchange exchange) throws Exception {
        System.out.println("→ POST /api/account/favorites");
        
        // Lire le corps de la requête
        String requestBody = readRequestBody(exchange);
        JsonObject jsonRequest = gson.fromJson(requestBody, JsonObject.class);
        
        String customerId = jsonRequest.get("customerId").getAsString();
        String cityName = jsonRequest.get("cityName").getAsString();
        
        if (customerId == null || cityName == null) {
            sendError(exchange, 400, "customerId et cityName sont requis");
            return;
        }
        
        System.out.println("  Customer: " + customerId);
        System.out.println("  City: " + cityName);
        
        // Appel CORBA avec timeout
        boolean added = timeoutExecutor.execute(() -> 
            accountManager.addFavoriteDestination(customerId, cityName),
            10, TimeUnit.SECONDS
        );
        
        if (added) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Destination ajoutée aux favorites");
            response.put("cityName", cityName);
            
            System.out.println("✅ Destination ajoutée: " + cityName);
            sendJsonResponse(exchange, 200, response);
        } else {
            sendError(exchange, 400, "Impossible d'ajouter la destination (déjà favorite ou limite atteinte)");
        }
    }
    
    /**
     * DELETE /api/account/favorites?customerId=xxx&cityName=yyy
     * Supprimer une destination favorite
     */
    private void handleDelete(HttpExchange exchange) throws Exception {
        System.out.println("→ DELETE /api/account/favorites");
        
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        String customerId = params.get("customerId");
        String cityName = params.get("cityName");
        
        if (customerId == null || cityName == null) {
            sendError(exchange, 400, "customerId et cityName sont requis");
            return;
        }
        
        System.out.println("  Customer: " + customerId);
        System.out.println("  City: " + cityName);
        
        // Appel CORBA avec timeout
        boolean removed = timeoutExecutor.execute(() -> 
            accountManager.removeFavoriteDestination(customerId, cityName),
            10, TimeUnit.SECONDS
        );
        
        if (removed) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Destination retirée des favorites");
            response.put("cityName", cityName);
            
            System.out.println("✅ Destination retirée: " + cityName);
            sendJsonResponse(exchange, 200, response);
        } else {
            sendError(exchange, 404, "Destination favorite introuvable");
        }
    }
    
    /**
     * OPTIONS - Préflight CORS
     */
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }
    
    // ==================== MÉTHODES UTILITAIRES ====================
    
    /**
     * Parser les paramètres de requête
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                try {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    params.put(key, value);
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Erreur décodage URL: " + e.getMessage());
                }
            }
        }
        return params;
    }
    
    /**
     * Lire le corps de la requête
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder body = new StringBuilder();
        String line;
        
        while ((line = br.readLine()) != null) {
            body.append(line);
        }
        
        return body.toString();
    }
    
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