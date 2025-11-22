// src/main/java/com/skybooking/rest/handlers/admin/DashboardHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 📊 Handler pour les statistiques du dashboard admin
 */
public class DashboardHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public DashboardHandler(AdminManager adminManager,
                           TimeoutExecutor timeoutExecutor) {
        this.adminManager = adminManager;
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
            System.out.println("→ Requête DASHBOARD STATS reçue");
            
            DashboardStats stats = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.getDashboardStats();
            }, Constants.TIMEOUT_DEFAULT, "dashboard stats");
            
            JsonObject response = new JsonObject();
            response.addProperty("totalFlights", stats.totalFlights);
            response.addProperty("totalBookings", stats.totalBookings);
            response.addProperty("totalCustomers", stats.totalCustomers);
            response.addProperty("totalRevenue", stats.totalRevenue);
            response.addProperty("todayBookings", stats.todayBookings);
            response.addProperty("pendingPayments", stats.pendingPayments);
            response.addProperty("availableSeats", stats.availableSeats);
            response.addProperty("occupancyRate", stats.occupancyRate);
            
            System.out.println("✅ Stats générées");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur dashboard: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}