// src/main/java/com/skybooking/rest/handlers/hotel/CancelHotelReservationHandler.java

package com.skybooking.rest.handlers.hotel;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.managers.impl.HotelManagerImpl;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * ❌ Handler pour annuler une réservation d'hôtel
 * Endpoint: POST /api/hotels/cancel
 */
public class CancelHotelReservationHandler implements HttpHandler {
    
    private final HotelManagerImpl hotelManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public CancelHotelReservationHandler(HotelManagerImpl hotelManager,
                                         TimeoutExecutor timeoutExecutor) {
        this.hotelManager = hotelManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            System.out.println("→ Requête CANCEL HOTEL RESERVATION reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            if (!json.has("hotelReservationId")) {
                ResponseHelper.sendError(exchange, 400, 
                    "Paramètre manquant: hotelReservationId");
                return;
            }
            
            String hotelReservationId = json.get("hotelReservationId").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "Client request";
            
            System.out.println("  Réservation hôtel: " + hotelReservationId);
            System.out.println("  Raison: " + reason);
            
            // Calculer le remboursement avant annulation
            double refundAmount = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.calculateHotelRefundAmount(hotelReservationId);
            }, Constants.TIMEOUT_DEFAULT, "calcul remboursement hôtel");
            
            // Annuler la réservation
            boolean cancelled = timeoutExecutor.executeWithTimeout(() -> {
                return hotelManager.cancelHotelReservation(hotelReservationId);
            }, Constants.TIMEOUT_CANCELLATION, "annulation hôtel");
            
            if (cancelled) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Réservation d'hôtel annulée avec succès");
                response.addProperty("hotelReservationId", hotelReservationId);
                response.addProperty("refundAmount", refundAmount);
                
                // Calculer le pourcentage de remboursement
                // (nécessite de récupérer le prix original)
                double refundPercentage = 0;
                try {
                    HotelReservation hotelRes = hotelManager.getHotelReservation(hotelReservationId);
                    if (hotelRes != null && hotelRes.finalPrice > 0) {
                        refundPercentage = (refundAmount / hotelRes.finalPrice) * 100;
                    }
                } catch (Exception e) {
                    // Ignorer l'erreur de calcul du pourcentage
                }
                
                response.addProperty("refundPercentage", refundPercentage);
                
                System.out.println("✅ Réservation hôtel annulée: " + hotelReservationId);
                System.out.println("  Remboursement: " + refundAmount + " DZD");
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, "Échec de l'annulation");
            }
            
        } catch (HotelBookingException e) {
            System.err.println("✗ Erreur annulation hôtel: " + e.message);
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (Exception e) {
            System.err.println("✗ Erreur serveur: " + e.getMessage());
            e.printStackTrace();
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}