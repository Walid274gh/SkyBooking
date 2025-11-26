// src/main/java/com/skybooking/rest/handlers/hotel/HotelDetailHandler.java

package com.skybooking.rest.handlers.hotel;

import com.sun.net.httpserver.*;
import FlightReservation.*;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 🔍 Handler pour obtenir les détails d'un hôtel
 */
public class HotelDetailHandler implements HttpHandler {
    
    private final HotelManager hotelManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson;
    
    public HotelDetailHandler(HotelManager hotelManager, TimeoutExecutor timeoutExecutor) {
        this.hotelManager = hotelManager;
        this.timeoutExecutor = timeoutExecutor;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            // Extraire l'ID de l'hôtel depuis l'URI
            String path = exchange.getRequestURI().getPath();
            String hotelId = path.substring(path.lastIndexOf('/') + 1);
            
            if (hotelId == null || hotelId.isEmpty()) {
                sendError(exchange, 400, "ID hôtel manquant");
                return;
            }
            
            System.out.println("🔍 Détails hôtel REST: " + hotelId);
            
            // Récupérer avec timeout
            Hotel hotel = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.getHotelById(hotelId);
            }, 10);
            
            // Convertir en JSON
            Map<String, Object> hotelMap = new LinkedHashMap<>();
            hotelMap.put("hotelId", hotel.hotelId);
            hotelMap.put("hotelName", hotel.hotelName);
            hotelMap.put("city", hotel.city);
            hotelMap.put("address", hotel.address);
            hotelMap.put("starRating", hotel.starRating);
            hotelMap.put("description", hotel.description);
            hotelMap.put("pricePerNight", hotel.pricePerNight);
            hotelMap.put("availableRooms", hotel.availableRooms);
            hotelMap.put("imageUrl", hotel.imageUrl);
            hotelMap.put("amenities", hotel.amenities);
            hotelMap.put("reviewScore", hotel.reviewScore);
            hotelMap.put("reviewCount", hotel.reviewCount);
            
            String jsonResponse = gson.toJson(hotelMap);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
            }
            
            System.out.println("✅ Détails hôtel retournés: " + hotel.hotelName);
            
        } catch (HotelNotFoundException e) {
            System.err.println("✗ Hôtel non trouvé: " + e.message);
            sendError(exchange, 404, e.message);
        } catch (Exception e) {
            System.err.println("✗ Erreur serveur: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Erreur serveur: " + e.getMessage());
        }
    }
    
    /**
     * Envoyer une erreur JSON
     */
    private void sendError(HttpExchange exchange, int statusCode, String message) 
            throws IOException {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        String jsonError = gson.toJson(error);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, jsonError.getBytes(StandardCharsets.UTF_8).length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonError.getBytes(StandardCharsets.UTF_8));
        }
    }
}
