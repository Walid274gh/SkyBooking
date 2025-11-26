// src/main/java/com/skybooking/rest/handlers/hotel/HotelReservationsHandler.java

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
 * 📋 Handler pour obtenir l'historique des réservations d'hôtels
 */
public class HotelReservationsHandler implements HttpHandler {
    
    private final HotelManager hotelManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson;
    
    public HotelReservationsHandler(HotelManager hotelManager, TimeoutExecutor timeoutExecutor) {
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
            // Extraire le customer ID depuis l'URI
            String path = exchange.getRequestURI().getPath();
            String customerId = path.substring(path.lastIndexOf('/') + 1);
            
            if (customerId == null || customerId.isEmpty()) {
                sendError(exchange, 400, "Customer ID manquant");
                return;
            }
            
            System.out.println("📋 Historique réservations hôtels REST: " + customerId);
            
            // ✅ CORRECTION: Ajout du 3ème paramètre (nom opération)
            HotelReservation[] reservations = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.getCustomerHotelReservations(customerId);
            }, 10, "historique hôtels");
            
            // Convertir en JSON
            List<Map<String, Object>> reservationsList = new ArrayList<>();
            for (HotelReservation res : reservations) {
                Map<String, Object> resMap = new LinkedHashMap<>();
                resMap.put("hotelReservationId", res.hotelReservationId);
                resMap.put("customerId", res.customerId);
                resMap.put("hotelId", res.hotelId);
                resMap.put("hotelName", res.hotelName);
                resMap.put("checkInDate", res.checkInDate);
                resMap.put("checkOutDate", res.checkOutDate);
                resMap.put("numberOfNights", res.numberOfNights);
                resMap.put("numberOfRooms", res.numberOfRooms);
                resMap.put("originalPrice", res.originalPrice);
                resMap.put("discountPercentage", res.discountPercentage);
                resMap.put("finalPrice", res.finalPrice);
                resMap.put("status", res.status);
                resMap.put("reservationDate", res.reservationDate);
                resMap.put("flightReservationId", res.flightReservationId);
                resMap.put("hasFlightDiscount", res.hasFlightDiscount);
                
                if (res.hasFlightDiscount) {
                    resMap.put("savings", res.originalPrice - res.finalPrice);
                }
                
                reservationsList.add(resMap);
            }
            
            String jsonResponse = gson.toJson(reservationsList);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
            }
            
            System.out.println("✅ " + reservations.length + " réservation(s) hôtel retournée(s)");
            
        } catch (Exception e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
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
