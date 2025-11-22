// src/main/java/com/skybooking/rest/handlers/admin/RevenueTrendHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import static com.skybooking.utils.Constants.*;
import FlightReservation.*;
import java.io.IOException;
import java.util.Map;

/**
 * 📈 Handler pour récupérer l'évolution des revenus (ADMIN)
 */
public class RevenueTrendHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public RevenueTrendHandler(AdminManager adminManager,
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
            Map<String, String> params = RequestHelper.parseQueryParams(
                exchange.getRequestURI()
            );
            
            String period = params.getOrDefault("period", "MONTHLY");
            String startDate = params.getOrDefault("startDate", "");
            String endDate = params.getOrDefault("endDate", "");
            
            System.out.println("→ Requête ADMIN REVENUE TREND");
            System.out.println("  Période: " + period);
            System.out.println("  Date début: " + startDate);
            System.out.println("  Date fin: " + endDate);
            
            // Appel CORBA avec timeout
            FinancialReport report = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.getFinancialReport(period, startDate, endDate);
            }, TIMEOUT_ADMIN, "récupération rapport financier");
            
            JsonObject response = new JsonObject();
            response.addProperty("period", report.period);
            response.addProperty("totalRevenue", report.totalRevenue);
            response.addProperty("totalRefunds", report.totalRefunds);
            response.addProperty("netRevenue", report.netRevenue);
            response.addProperty("totalTransactions", report.totalTransactions);
            response.addProperty("averageBookingValue", report.averageBookingValue);
            response.addProperty("topRoute", report.topRoute);
            response.addProperty("reportDate", report.reportDate);
            
            System.out.println("✅ Rapport financier généré");
            System.out.println("  Revenus nets: " + report.netRevenue + " DZD");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération revenus: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
