// src/main/java/com/skybooking/rest/handlers/admin/AdminLoginHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 👨‍💼 Handler pour la connexion admin
 */
public class AdminLoginHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public AdminLoginHandler(AdminManager adminManager,
                            TimeoutExecutor timeoutExecutor) {
        this.adminManager = adminManager;
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
            System.out.println("→ Requête ADMIN LOGIN reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null || !json.has("username") || !json.has("password")) {
                ResponseHelper.sendError(exchange, 400, "Paramètres manquants");
                return;
            }
            
            String username = json.get("username").getAsString();
            String password = json.get("password").getAsString();
            
            AdminUser admin = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.adminLogin(username, password);
            }, Constants.TIMEOUT_DEFAULT, "admin login");
            
            // Générer un token de session
            String adminId = admin.adminId;
            String token = "TOKEN_" + System.currentTimeMillis();
            
            JsonObject response = new JsonObject();
            response.addProperty("adminId", admin.adminId);
            response.addProperty("username", admin.username);
            response.addProperty("email", admin.email);
            response.addProperty("role", admin.role);
            response.addProperty("token", token);
            
            System.out.println("✅ Admin connecté: " + username);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur admin login: " + e.getMessage());
            ResponseHelper.sendError(exchange, 401, e.getMessage());
        }
    }
}
