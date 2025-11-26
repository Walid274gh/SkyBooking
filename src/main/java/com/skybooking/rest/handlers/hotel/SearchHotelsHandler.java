// src/main/java/com/skybooking/rest/handlers/hotel/SearchHotelsHandler.java

package com.skybooking.rest.handlers.hotel;

import com.sun.net.httpserver.*;
import FlightReservation.*;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 🔍 Handler pour la recherche d'hôtels
 */
public class SearchHotelsHandler implements HttpHandler {
    
    private final HotelManager hotelManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson;
    
    public SearchHotelsHandler(HotelManager hotelManager, TimeoutExecutor timeoutExecutor) {
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
            // Parser les paramètres de recherche
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            
            String city = params.get("city");
            String checkInDate = params.get("checkInDate");
            String checkOutDate = params.get("checkOutDate");
            int numberOfRooms = Integer.parseInt(params.getOrDefault("numberOfRooms", "1"));
            int minStarRating = Integer.parseInt(params.getOrDefault("minStarRating", "0"));
            String flightReservationId = params.getOrDefault("flightReservationId", "");
            
            System.out.println("🔍 Recherche hôtels REST: " + city + " | " + 
                             checkInDate + " → " + checkOutDate);
            
            // ✅ CORRECTION: Ajout du 3ème paramètre (nom opération)
            Hotel[] hotels = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.searchHotels(
                    city, checkInDate, checkOutDate, 
                    numberOfRooms, minStarRating
                );
            }, 15, "recherche hôtels");
            
            // Convertir en JSON avec indicateur de réduction si vol lié
            List<Map<String, Object>> hotelsWithDiscount = new ArrayList<>();
            boolean hasFlightDiscount = flightReservationId != null && 
                                       !flightReservationId.trim().isEmpty();
            
            for (Hotel hotel : hotels) {
                Map<String, Object> hotelMap = hotelToMap(hotel);
                
                // Ajouter l'information de réduction Dynamic Packaging
                if (hasFlightDiscount) {
                    hotelMap.put("hasFlightDiscount", true);
                    hotelMap.put("discountPercentage", 15.0);
                    hotelMap.put("discountedPrice", hotel.pricePerNight * 0.85);
                }
                
                hotelsWithDiscount.add(hotelMap);
            }
            
            String jsonResponse = gson.toJson(hotelsWithDiscount);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
            }
            
            System.out.println("✅ " + hotels.length + " hôtel(s) retourné(s)");
            
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Paramètres invalides");
        } catch (Exception e) {
            System.err.println("❌ Erreur recherche hôtels: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Erreur lors de la recherche: " + e.getMessage());
        }
    }
    
    /**
     * Convertir Hotel CORBA en Map
     */
    private Map<String, Object> hotelToMap(Hotel hotel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hotelId", hotel.hotelId);
        map.put("hotelName", hotel.hotelName);
        map.put("city", hotel.city);
        map.put("address", hotel.address);
        map.put("starRating", hotel.starRating);
        map.put("description", hotel.description);
        map.put("pricePerNight", hotel.pricePerNight);
        map.put("availableRooms", hotel.availableRooms);
        map.put("imageUrl", hotel.imageUrl);
        map.put("amenities", hotel.amenities);
        map.put("reviewScore", hotel.reviewScore);
        map.put("reviewCount", hotel.reviewCount);
        return map;
    }
    
    /**
     * Parser les paramètres de requête
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    try {
                        params.put(
                            URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name()),
                            URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())
                        );
                    } catch (Exception e) {
                        // Ignorer les paramètres mal formés
                    }
                }
            }
        }
        return params;
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
