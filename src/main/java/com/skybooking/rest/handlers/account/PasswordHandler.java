// src/main/java/com/skybooking/rest/handlers/account/PasswordHandler.java

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
 * 🔐 Handler pour la gestion des mots de passe
 */
public class PasswordHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public PasswordHandler(AccountManager accountManager,
                          TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        String path = exchange.getRequestURI().getPath();
        
        try {
            if (path.endsWith("/change")) {
                handleChangePassword(exchange);
            } else if (path.endsWith("/reset-request")) {
                handleRequestReset(exchange);
            } else if (path.endsWith("/reset")) {
                handleResetPassword(exchange);
            } else {
                ResponseHelper.sendError(exchange, 404, "Endpoint non trouvé");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur password handler: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
    
    /**
     * Changer le mot de passe
     */
    private void handleChangePassword(HttpExchange exchange) throws IOException {
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null || !json.has("customerId") || 
                !json.has("currentPassword") || !json.has("newPassword")) {
                ResponseHelper.sendError(exchange, 400, "Paramètres manquants");
                return;
            }
            
            String customerId = json.get("customerId").getAsString();
            String currentPassword = json.get("currentPassword").getAsString();
            String newPassword = json.get("newPassword").getAsString();
            
            if (!ValidationUtils.isValidPassword(newPassword)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Le nouveau mot de passe doit contenir au moins " + 
                    Constants.MIN_PASSWORD_LENGTH + " caractères");
                return;
            }
            
            boolean success = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.changePassword(customerId, currentPassword, newPassword);
            }, Constants.TIMEOUT_DEFAULT, "changement mot de passe");
            
            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            response.addProperty("message", "Mot de passe changé avec succès");
            
            System.out.println("✅ Mot de passe changé: " + customerId);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (InvalidPasswordException e) {
            ResponseHelper.sendError(exchange, 401, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur (timeout ?) lors du changement de mot de passe: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, "Erreur serveur: " + e.getMessage());
        }
    }
    
    /**
     * Demander une réinitialisation
     */
    private void handleRequestReset(HttpExchange exchange) throws IOException {
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null || !json.has("email")) {
                ResponseHelper.sendError(exchange, 400, "Email manquant");
                return;
            }
            
            String email = json.get("email").getAsString();
            
            if (!ValidationUtils.isValidEmail(email)) {
                ResponseHelper.sendError(exchange, 400, "Format d'email invalide");
                return;
            }
            
            String token = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.requestPasswordReset(email);
            }, Constants.TIMEOUT_DEFAULT, "demande reset password");
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", 
                "Si cet email existe, un lien de réinitialisation a été envoyé");
            response.addProperty("token", token); // En prod, ne pas renvoyer le token !
            
            System.out.println("✅ Reset demandé pour: " + email);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            // Ne pas révéler si l'email existe
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", 
                "Si cet email existe, un lien de réinitialisation a été envoyé");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Réinitialiser le mot de passe
     */
    private void handleResetPassword(HttpExchange exchange) throws IOException {
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null || !json.has("email") || 
                !json.has("token") || !json.has("newPassword")) {
                ResponseHelper.sendError(exchange, 400, "Paramètres manquants");
                return;
            }
            
            String newPassword = json.get("newPassword").getAsString();
            
            if (!ValidationUtils.isValidPassword(newPassword)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Le mot de passe doit contenir au moins " + 
                    Constants.MIN_PASSWORD_LENGTH + " caractères");
                return;
            }
            
            PasswordReset resetData = new PasswordReset(
                json.get("email").getAsString(),
                json.get("token").getAsString(),
                newPassword
            );
            
            boolean success = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.resetPassword(resetData);
            }, Constants.TIMEOUT_DEFAULT, "reset password");
            
            JsonObject response = new JsonObject();
            response.addProperty("success", success);
            response.addProperty("message", "Mot de passe réinitialisé avec succès");
            
            System.out.println("✅ Mot de passe réinitialisé");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (ResetTokenExpiredException e) {
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur (timeout ?) lors de la réinitialisation du mot de passe: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, "Erreur serveur: " + e.getMessage());
        }
    }
}