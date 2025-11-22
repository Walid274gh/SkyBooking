// src/main/java/com/skybooking/rest/handlers/cancellation/CancelReservationHandler.java

package com.skybooking.rest.handlers.cancellation;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import static com.skybooking.utils.Constants.*;
import FlightReservation.*;
import java.io.IOException;

/**
 * ❌ Handler pour annuler une réservation
 */
public class CancelReservationHandler implements HttpHandler {
    
    private final CancellationManager cancellationManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public CancelReservationHandler(CancellationManager cancellationManager,
                                   TimeoutExecutor timeoutExecutor) {
        this.cancellationManager = cancellationManager;
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
            System.out.println("→ Requête CANCEL RESERVATION reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            if (!json.has("reservationId") || !json.has("reason")) {
                ResponseHelper.sendError(exchange, 400, 
                    "Paramètres manquants: reservationId, reason");
                return;
            }
            
            String reservationId = json.get("reservationId").getAsString();
            String reason = json.get("reason").getAsString();
            
            System.out.println("  Réservation: " + reservationId);
            System.out.println("  Raison: " + reason);
            
            // Récupérer la politique d'annulation
            CancellationPolicy policy = timeoutExecutor.executeWithTimeout(() -> {
                return cancellationManager.getCancellationPolicy(reservationId);
            }, TIMEOUT_DEFAULT, "récupération politique annulation");
            
            // Calculer le montant du remboursement
            double refundAmount = timeoutExecutor.executeWithTimeout(() -> {
                return cancellationManager.calculateRefundAmount(reservationId);
            }, TIMEOUT_DEFAULT, "calcul remboursement");
            
            // Appel CORBA avec timeout
            boolean cancelled = timeoutExecutor.executeWithTimeout(() -> {
                return cancellationManager.cancelReservation(reservationId, reason);
            }, TIMEOUT_CANCELLATION, "annulation réservation");
            
            if (cancelled) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Réservation annulée avec succès");
                response.addProperty("reservationId", reservationId);
                response.addProperty("refundAmount", refundAmount);
                response.addProperty("refundPercentage", policy.refundPercentage);
                
                System.out.println("✅ Réservation annulée: " + reservationId);
                System.out.println("  Remboursement: " + refundAmount + " DZD");
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, "Échec de l'annulation");
            }
            
        } catch (CancellationNotAllowedException e) {
            System.err.println("✗ Annulation non autorisée: " + e.message);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.message);
            error.addProperty("hoursRemaining", e.hoursRemaining);
            ResponseHelper.sendJsonResponse(exchange, 403, error);
        } catch (Exception e) {
            System.err.println("✗ Erreur serveur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
