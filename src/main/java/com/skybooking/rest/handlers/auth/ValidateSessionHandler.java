// src/main/java/com/skybooking/rest/handlers/auth/ValidateSessionHandler.java

package com.skybooking.rest.handlers.auth;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import FlightReservation.*;
import java.io.IOException;

/**
 * ✅ Handler pour valider une session utilisateur (SÉCURISÉ)
 */
public class ValidateSessionHandler implements HttpHandler {
    
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public ValidateSessionHandler(CustomerManager customerManager,
                                 TimeoutExecutor timeoutExecutor) {
        this.customerManager = customerManager;
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
            System.out.println("→ Requête VALIDATE SESSION reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            
            if (body == null || body.trim().isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "Corps de requête vide");
                return;
            }
            
            JsonObject json = JsonHelper.parseJson(body);
            if (json == null || !json.has("customerId") || !json.has("sessionToken")) {
                ResponseHelper.sendError(exchange, 400, "customerId et sessionToken requis");
                return;
            }
            
            String customerId = json.get("customerId").getAsString();
            String sessionToken = json.get("sessionToken").getAsString();
            
            // 🔐 VALIDATION SÉCURISÉE AVEC TOKEN - APPEL DIRECT
            Customer customer = null;
            
            try {
                customer = timeoutExecutor.executeWithTimeout(() -> {
                    return customerManager.validateSession(customerId, sessionToken);
                }, 5, "validation session");
                
            } catch (InvalidCredentialsException e) {
                System.err.println("✗ Session invalide: " + e.message);
                ResponseHelper.sendError(exchange, 401, "Session invalide ou expirée");
                return;
            }
            
            if (customer == null) {
                ResponseHelper.sendError(exchange, 401, "Session invalide ou expirée");
                return;
            }
            
            // Session valide - renvoyer les infos utilisateur
            JsonObject response = JsonHelper.customerToJson(customer);
            response.addProperty("valid", true);
            response.addProperty("sessionToken", sessionToken); // Renvoyer le même token
            
            System.out.println("✅ Session valide pour: " + customer.username);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur validation session: " + e.getMessage());
            e.printStackTrace();
            ResponseHelper.sendError(exchange, 401, "Session invalide");
        }
    }
}