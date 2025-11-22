// src/main/java/com/skybooking/rest/handlers/auth/RegisterHandler.java

package com.skybooking.rest.handlers.auth;

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
 * 🔐 Handler pour l'inscription des utilisateurs (SÉCURISÉ)
 * Fusion: Validation complète + Sessions avec tokens
 */
public class RegisterHandler implements HttpHandler {
    
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public RegisterHandler(CustomerManager customerManager,
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
            System.out.println("→ Requête REGISTER reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            
            if (body == null || body.trim().isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "Corps de requête vide");
                return;
            }
            
            JsonObject json = JsonHelper.parseJson(body);
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            String[] requiredFields = {"username", "password", "firstName", 
                                       "lastName", "email", "phoneNumber"};
            for (String field : requiredFields) {
                if (!json.has(field) || json.get(field).getAsString().trim().isEmpty()) {
                    ResponseHelper.sendError(exchange, 400, 
                        "Paramètre manquant ou vide: " + field);
                    return;
                }
            }
            
            String username = json.get("username").getAsString().trim();
            String password = json.get("password").getAsString();
            String firstName = json.get("firstName").getAsString().trim();
            String lastName = json.get("lastName").getAsString().trim();
            String email = json.get("email").getAsString().trim();
            String phoneNumber = json.get("phoneNumber").getAsString().trim();
            
            // Validations complètes
            if (!ValidationUtils.isValidUsername(username)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Le nom d'utilisateur doit contenir au moins " + 
                    Constants.MIN_USERNAME_LENGTH + " caractères");
                return;
            }
            
            if (!ValidationUtils.isValidPassword(password)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Le mot de passe doit contenir au moins " + 
                    Constants.MIN_PASSWORD_LENGTH + " caractères");
                return;
            }
            
            if (!ValidationUtils.isNotEmpty(firstName) || !ValidationUtils.isNotEmpty(lastName)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Le prénom et le nom ne peuvent pas être vides");
                return;
            }
            
            if (!ValidationUtils.isValidEmail(email)) {
                ResponseHelper.sendError(exchange, 400, "Format d'email invalide");
                return;
            }
            
            if (!ValidationUtils.isValidPhone(phoneNumber)) {
                ResponseHelper.sendError(exchange, 400, 
                    "Format de téléphone invalide (10-15 chiffres, peut commencer par +)");
                return;
            }
            
            String cleanPhone = ValidationUtils.cleanPhoneNumber(phoneNumber);
            
            System.out.println("  Nouvel utilisateur: " + username);
            
            // ✅ Créer variables final pour lambda
            final String finalUsername = username;
            final String finalPassword = password;
            final String finalFirstName = firstName;
            final String finalLastName = lastName;
            final String finalEmail = email;
            final String finalCleanPhone = cleanPhone;
            
            // ✅ Appel CORBA avec timeout 
            LoginResponse loginResponse = timeoutExecutor.executeWithTimeout(() -> {
                return customerManager.registerCustomer(
                    finalUsername, finalPassword, finalFirstName, 
                    finalLastName, finalEmail, finalCleanPhone
                );
            }, Constants.TIMEOUT_DEFAULT, "register");
            
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
            
            System.out.println("✅ Inscription réussie pour: " + username);
            System.out.println("   Client ID: " + customer.customerId);
            
            ResponseHelper.sendJsonResponse(exchange, 201, response);
            
        } catch (CustomerAlreadyExistsException e) {
            System.err.println("✗ Échec inscription: " + e.message);
            ResponseHelper.sendError(exchange, 409, e.message);
        } catch (Exception e) {
            System.err.println("✗ Erreur inscription: " + e.getMessage());
            e.printStackTrace();
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}