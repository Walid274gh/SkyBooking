// src/main/java/com/skybooking/rest/handlers/admin/UserManagementHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonArray;
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
 * 👥 Handler pour la gestion des utilisateurs (ADMIN)
 */
public class UserManagementHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public UserManagementHandler(AdminManager adminManager,
                                TimeoutExecutor timeoutExecutor) {
        this.adminManager = adminManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        String method = exchange.getRequestMethod();
        
        if ("GET".equals(method)) {
            handleGetUsers(exchange);
        } else if ("POST".equals(method)) {
            handleUserAction(exchange);
        } else if ("DELETE".equals(method)) {
            handleDeleteUser(exchange);
        } else {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
        }
    }
    
    private void handleGetUsers(HttpExchange exchange) throws IOException {
        try {
            System.out.println("→ Requête ADMIN GET ALL USERS");
            
            // Appel CORBA avec timeout
            UserManagementData[] users = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.getAllUsers();
            }, TIMEOUT_ADMIN, "récupération utilisateurs");
            
            JsonArray usersArray = new JsonArray();
            for (UserManagementData user : users) {
                JsonObject userJson = new JsonObject();
                userJson.addProperty("customerId", user.customerId);
                userJson.addProperty("username", user.username);
                userJson.addProperty("firstName", user.firstName);
                userJson.addProperty("lastName", user.lastName);
                userJson.addProperty("email", user.email);
                userJson.addProperty("phoneNumber", user.phoneNumber);
                userJson.addProperty("totalBookings", user.totalBookings);
                userJson.addProperty("totalSpent", user.totalSpent);
                userJson.addProperty("lastBooking", user.lastBooking);
                userJson.addProperty("isActive", user.isActive);
                userJson.addProperty("registeredAt", user.registeredAt);
                usersArray.add(userJson);
            }
            
            JsonObject response = new JsonObject();
            response.add("users", usersArray);
            response.addProperty("count", users.length);
            
            System.out.println("✅ " + users.length + " utilisateur(s) trouvé(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération utilisateurs: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
    
    private void handleUserAction(HttpExchange exchange) throws IOException {
        try {
            System.out.println("→ Requête ADMIN USER ACTION");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            if (!json.has("action") || !json.has("customerId")) {
                ResponseHelper.sendError(exchange, 400, 
                    "Paramètres manquants: action, customerId");
                return;
            }
            
            String action = json.get("action").getAsString();
            String customerId = json.get("customerId").getAsString();
            
            System.out.println("  Action: " + action);
            System.out.println("  Client: " + customerId);
            
            boolean success = false;
            String message = "";
            
            if ("suspend".equals(action)) {
                String reason = json.has("reason") 
                    ? json.get("reason").getAsString() 
                    : "Suspension administrative";
                
                success = timeoutExecutor.executeWithTimeout(() -> {
                    return adminManager.suspendUser(customerId, reason);
                }, TIMEOUT_ADMIN, "suspension utilisateur");
                
                message = "Utilisateur suspendu avec succès";
                
            } else if ("activate".equals(action)) {
                success = timeoutExecutor.executeWithTimeout(() -> {
                    return adminManager.activateUser(customerId);
                }, TIMEOUT_ADMIN, "activation utilisateur");
                
                message = "Utilisateur activé avec succès";
                
            } else {
                ResponseHelper.sendError(exchange, 400, "Action invalide");
                return;
            }
            
            if (success) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", message);
                response.addProperty("customerId", customerId);
                
                System.out.println("✅ " + message);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, "Échec de l'action");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur action utilisateur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
    
    private void handleDeleteUser(HttpExchange exchange) throws IOException {
        try {
            String customerId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (customerId == null || customerId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID client invalide");
                return;
            }
            
            System.out.println("→ Requête ADMIN DELETE USER: " + customerId);
            
            // Appel CORBA avec timeout
            boolean deleted = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.deleteUser(customerId);
            }, TIMEOUT_ADMIN, "suppression utilisateur");
            
            if (deleted) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Utilisateur supprimé avec succès");
                response.addProperty("customerId", customerId);
                
                System.out.println("✅ Utilisateur supprimé: " + customerId);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, 
                    "Échec de la suppression (réservations actives)");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur suppression utilisateur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
