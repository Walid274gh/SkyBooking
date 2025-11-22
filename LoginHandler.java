// src/main/java/com/skybooking/rest/handlers/auth/LoginHandler.java

package com.skybooking.rest.handlers.auth;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.security.RateLimiter;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import com.skybooking.utils.ValidationUtils;
import FlightReservation.*;
import java.io.IOException;

/**
 * 🔐 Handler pour la connexion des utilisateurs (SÉCURISÉ)
 * Fusion: Rate limiting + Sessions avec tokens
 */
public class LoginHandler implements HttpHandler {
    
    private final CustomerManager customerManager;
    private final RateLimiter rateLimiter;
    private final TimeoutExecutor timeoutExecutor;
    
    public LoginHandler(CustomerManager customerManager, 
                       RateLimiter rateLimiter,
                       TimeoutExecutor timeoutExecutor) {
        this.customerManager = customerManager;
        this.rateLimiter = rateLimiter;
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
        
        // Rate Limiting
        String clientIP = RequestHelper.getClientIP(exchange);
        String username = null;
        
        // Vérifier le rate limit par IP
        if (!rateLimiter.isAllowed(clientIP)) {
            RateLimiter.LockoutInfo lockoutInfo = rateLimiter.getLockoutInfo(clientIP);
            
            String errorMessage = String.format(
                "Trop de tentatives de connexion. Compte bloqué pendant %d minutes.",
                lockoutInfo.remainingMinutes
            );
            
            System.err.println("⚠️ Accès bloqué: " + clientIP + " - " + lockoutInfo.toString());
            ResponseHelper.sendError(exchange, 429, errorMessage);
            return;
        }
        
        try {
            System.out.println("→ Requête LOGIN de: " + clientIP);
            
            String body = RequestHelper.readRequestBody(exchange);
            
            if (body == null || body.trim().isEmpty()) {
                rateLimiter.recordFailedAttempt(clientIP, clientIP);
                ResponseHelper.sendError(exchange, 400, "Corps de requête vide");
                return;
            }
            
            JsonObject json = JsonHelper.parseJson(body);
            if (json == null) {
                rateLimiter.recordFailedAttempt(clientIP, clientIP);
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des paramètres
            if (!json.has("username") || !json.has("password")) {
                rateLimiter.recordFailedAttempt(clientIP, clientIP);
                ResponseHelper.sendError(exchange, 400, 
                    "Paramètres manquants: username et password requis");
                return;
            }
            
            username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();
            
            if (!ValidationUtils.isValidUsername(username)) {
                rateLimiter.recordFailedAttempt(clientIP, clientIP);
                ResponseHelper.sendError(exchange, 400, 
                    "Le nom d'utilisateur doit contenir au moins " + 
                    Constants.MIN_USERNAME_LENGTH + " caractères");
                return;
            }
            
            if (!ValidationUtils.isValidPassword(password)) {
                rateLimiter.recordFailedAttempt(clientIP, clientIP);
                ResponseHelper.sendError(exchange, 400, 
                    "Le mot de passe doit contenir au moins " + 
                    Constants.MIN_PASSWORD_LENGTH + " caractères");
                return;
            }
            
            System.out.println("  Utilisateur: " + username);
            
            // Vérifier aussi le rate limit par username
            String usernameIdentifier = "user:" + username;
            if (!rateLimiter.isAllowed(usernameIdentifier)) {
                RateLimiter.LockoutInfo lockoutInfo = rateLimiter.getLockoutInfo(usernameIdentifier);
                
                String errorMessage = String.format(
                    "Ce compte est temporairement bloqué. Réessayez dans %d minutes.",
                    lockoutInfo.remainingMinutes
                );
                
                System.err.println("⚠️ Compte bloqué: " + username);
                ResponseHelper.sendError(exchange, 429, errorMessage);
                return;
            }
            
            // ✅ Créer variables final pour lambda
            final String finalUsername = username;
            final String finalPassword = password;
            
            // ✅ Appel CORBA avec timeout 
            LoginResponse loginResponse = timeoutExecutor.executeWithTimeout(() -> {
                return customerManager.login(finalUsername, finalPassword);
            }, Constants.TIMEOUT_LOGIN, "login");
            
            // Enregistrer tentative réussie
            rateLimiter.recordSuccessfulAttempt(clientIP, clientIP);
            rateLimiter.recordSuccessfulAttempt(usernameIdentifier, clientIP);
            
            // ✅ Extraire Customer et Token de LoginResponse
            Customer customer = loginResponse.customer;
            String sessionToken = loginResponse.sessionToken;
            
            // Construire la réponse avec le token
            JsonObject response = JsonHelper.customerToJson(customer);
            
            if (sessionToken != null && !sessionToken.isEmpty()) {
                response.addProperty("sessionToken", sessionToken);
                System.out.println("   🔑 Token généré: " + sessionToken.substring(0, 16) + "...");
            } else {
                System.err.println("   ⚠️ AVERTISSEMENT: Pas de token généré!");
            }
            
            System.out.println("✅ Connexion réussie pour: " + username);
            System.out.println("   Client ID: " + customer.customerId);
            
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (InvalidCredentialsException e) {
            // Enregistrer tentative échouée
            rateLimiter.recordFailedAttempt(clientIP, clientIP);
            
            if (username != null) {
                String usernameIdentifier = "user:" + username;
                rateLimiter.recordFailedAttempt(usernameIdentifier, clientIP);
                
                // Obtenir info sur les tentatives restantes
                RateLimiter.LockoutInfo lockoutInfo = rateLimiter.getLockoutInfo(usernameIdentifier);
                System.err.println("⚠️ Échec login: " + username + " - " + lockoutInfo.message);
            }
            
            System.err.println("✗ Échec login: " + e.message);
            ResponseHelper.sendError(exchange, 401, e.message);
            
        } catch (Exception e) {
            // Enregistrer tentative échouée
            rateLimiter.recordFailedAttempt(clientIP, clientIP);
            
            if (username != null) {
                rateLimiter.recordFailedAttempt("user:" + username, clientIP);
            }
            
            System.err.println("✗ Erreur login: " + e.getMessage());
            e.printStackTrace();
            ResponseHelper.sendError(exchange, 500, 
                "Erreur serveur lors de la connexion");
        }
    }
}