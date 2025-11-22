// src/main/java/com/skybooking/rest/handlers/account/ProfileHandler.java

package com.skybooking.rest.handlers.account;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import com.skybooking.utils.ValidationUtils;
import FlightReservation.*;
import java.io.IOException;

/**
 * 👤 Handler pour la mise à jour du profil
 */
public class ProfileHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public ProfileHandler(AccountManager accountManager,
                         TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "PUT")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            System.out.println("→ Requête UPDATE PROFILE reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            String[] requiredFields = {"customerId", "firstName", "lastName", 
                                       "email", "phoneNumber", "currentPassword"};
            for (String field : requiredFields) {
                if (!json.has(field)) {
                    ResponseHelper.sendError(exchange, 400, 
                        "Paramètre manquant: " + field);
                    return;
                }
            }
            
            // Validations
            if (!ValidationUtils.isValidEmail(json.get("email").getAsString())) {
                ResponseHelper.sendError(exchange, 400, "Format d'email invalide");
                return;
            }
            
            if (!ValidationUtils.isValidPhone(json.get("phoneNumber").getAsString())) {
                ResponseHelper.sendError(exchange, 400, "Format de téléphone invalide");
                return;
            }
            
            ProfileUpdate profileData = new ProfileUpdate(
                json.get("customerId").getAsString(),
                json.get("firstName").getAsString(),
                json.get("lastName").getAsString(),
                json.get("email").getAsString(),
                json.get("phoneNumber").getAsString(),
                json.get("currentPassword").getAsString(),
                json.has("newPassword") ? json.get("newPassword").getAsString() : ""
            );
            
            boolean success = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.updateProfile(profileData);
            }, Constants.TIMEOUT_DEFAULT, "mise à jour profil");
            
            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            response.addProperty("message", "Profil mis à jour avec succès");
            
            System.out.println("✅ Profil mis à jour: " + profileData.customerId);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (InvalidPasswordException e) {
            System.err.println("❌ Mot de passe invalide: " + e.message);
            ResponseHelper.sendError(exchange, 401, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur mise à jour profil: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}