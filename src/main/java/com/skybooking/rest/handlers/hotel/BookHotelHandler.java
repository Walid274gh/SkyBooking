// src/main/java/com/skybooking/rest/handlers/hotel/BookHotelHandler.java

package com.skybooking.rest.handlers.hotel;

import com.sun.net.httpserver.*;
import FlightReservation.*;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 📝 Handler pour réserver un hôtel
 */
public class BookHotelHandler implements HttpHandler {
    
    private final HotelManager hotelManager;
    private final TimeoutExecutor timeoutExecutor;
    private final Gson gson;
    
    public BookHotelHandler(HotelManager hotelManager, TimeoutExecutor timeoutExecutor) {
        this.hotelManager = hotelManager;
        this.timeoutExecutor = timeoutExecutor;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            // Lire le corps de la requête
            String requestBody;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                requestBody = sb.toString();
            }
            
            // Parser le JSON
            JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();
            
            String customerId = json.get("customerId").getAsString();
            String hotelId = json.get("hotelId").getAsString();
            String checkInDate = json.get("checkInDate").getAsString();
            String checkOutDate = json.get("checkOutDate").getAsString();
            int numberOfRooms = json.get("numberOfRooms").getAsInt();
            String flightReservationId = json.has("flightReservationId") 
                ? json.get("flightReservationId").getAsString() : "";
            
            System.out.println("📝 Réservation hôtel REST: " + hotelId + 
                             " | Client: " + customerId);
            if (!flightReservationId.isEmpty()) {
                System.out.println("✨ Dynamic Packaging: Vol lié " + flightReservationId);
            }
            
            // ✅ CORRECTION: Ajout du 3ème paramètre (nom opération)
            HotelReservation reservation = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.bookHotel(
                    customerId, hotelId, checkInDate, checkOutDate,
                    numberOfRooms, flightReservationId
                );
            }, 20, "réservation hôtel");
            
            // Convertir en JSON
            Map<String, Object> response = hotelReservationToMap(reservation);
            String jsonResponse = gson.toJson(response);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonResponse.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(jsonResponse.getBytes(StandardCharsets.UTF_8));
            }
            
            System.out.println("✅ Réservation hôtel créée: " + 
                             reservation.hotelReservationId);
            
            if (reservation.hasFlightDiscount) {
                System.out.println("💰 Réduction appliquée: -" + 
                                 reservation.discountPercentage + "%");
            }
            
        } catch (HotelBookingException e) {
            System.err.println("❌ Erreur réservation: " + e.message);
            sendError(exchange, 400, e.message);
        } catch (NoRoomsAvailableException e) {
            System.err.println("❌ Plus de chambres: " + e.message);
            sendError(exchange, 409, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, "Erreur lors de la réservation: " + e.getMessage());
        }
    }
    
    /**
     * Convertir HotelReservation CORBA en Map
     */
    private Map<String, Object> hotelReservationToMap(HotelReservation res) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hotelReservationId", res.hotelReservationId);
        map.put("customerId", res.customerId);
        map.put("hotelId", res.hotelId);
        map.put("hotelName", res.hotelName);
        map.put("checkInDate", res.checkInDate);
        map.put("checkOutDate", res.checkOutDate);
        map.put("numberOfNights", res.numberOfNights);
        map.put("numberOfRooms", res.numberOfRooms);
        map.put("originalPrice", res.originalPrice);
        map.put("discountPercentage", res.discountPercentage);
        map.put("finalPrice", res.finalPrice);
        map.put("status", res.status);
        map.put("reservationDate", res.reservationDate);
        map.put("flightReservationId", res.flightReservationId);
        map.put("hasFlightDiscount", res.hasFlightDiscount);
        
        if (res.hasFlightDiscount) {
            double savings = res.originalPrice - res.finalPrice;
            map.put("savings", savings);
        }
        
        return map;
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
