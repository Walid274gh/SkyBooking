// src/main/java/com/skybooking/rest/handlers/account/BookingHistoryHandler.java

package com.skybooking.rest.handlers.account;

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
import java.util.Map;

/**
 * 📋 Handler pour l'historique des réservations
 */
public class BookingHistoryHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public BookingHistoryHandler(AccountManager accountManager,
                                TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
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
            Map<String, String> params = RequestHelper.parseQueryParams(exchange.getRequestURI());
            String customerId = params.get("customerId");
            
            if (customerId == null || customerId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "customerId requis");
                return;
            }
            
            System.out.println("→ Requête BOOKING HISTORY pour: " + customerId);
            
            BookingHistory[] history = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.getBookingHistory(customerId);
            }, Constants.TIMEOUT_DEFAULT, "historique réservations");
            
            JsonArray jsonArray = new JsonArray();
            for (BookingHistory item : history) {
                JsonObject json = new JsonObject();
                json.addProperty("reservationId", item.reservationId);
                json.addProperty("flightNumber", item.flightNumber);
                json.addProperty("departureCity", item.departureCity);
                json.addProperty("arrivalCity", item.arrivalCity);
                json.addProperty("departureDate", item.departureDate);
                json.addProperty("status", item.status);
                json.addProperty("totalPrice", item.totalPrice);
                json.addProperty("bookingDate", item.bookingDate);
                json.addProperty("canCancel", item.canCancel);
                json.addProperty("canModify", item.canModify);
                jsonArray.add(json);
            }
            
            System.out.println("✅ " + history.length + " réservation(s) retournée(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, jsonArray);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur historique: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
