// src/main/java/com/skybooking/rest/handlers/admin/TopRoutesHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonArray;
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
 * 📊 Handler pour récupérer les routes les plus populaires (ADMIN)
 */
public class TopRoutesHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public TopRoutesHandler(AdminManager adminManager,
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
            
            int limit = 10; // Par défaut
            if (params.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(params.get("limit"));
                } catch (NumberFormatException e) {
                    limit = 10;
                }
            }
            
            System.out.println("→ Requête ADMIN TOP ROUTES (limit: " + limit + ")");
            
            final int finalLimit = limit;
            
            // Appel CORBA avec timeout
            String[] topRoutes = timeoutExecutor.executeWithTimeout(() -> {
                return adminManager.getTopRoutes(finalLimit);
            }, TIMEOUT_ADMIN, "récupération top routes");
            
            JsonArray routesArray = new JsonArray();
            for (String route : topRoutes) {
                routesArray.add(route);
            }
            
            JsonObject response = new JsonObject();
            response.add("topRoutes", routesArray);
            response.addProperty("count", topRoutes.length);
            
            System.out.println("✅ " + topRoutes.length + " route(s) récupérée(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération top routes: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
