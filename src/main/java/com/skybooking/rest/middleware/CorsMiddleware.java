// src/main/java/com/skybooking/rest/middleware/CorsMiddleware.java

package com.skybooking.rest.middleware;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/**
 * 🌐 Middleware CORS pour autoriser les requêtes cross-origin
 */
public class CorsMiddleware {
    
    /**
     * Configurer les headers CORS
     */
    public static void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        headers.set("Access-Control-Max-Age", "3600");
        headers.set("Content-Type", "application/json; charset=UTF-8");
    }
    
    /**
     * Gérer les requêtes OPTIONS (preflight)
     */
    public static boolean handlePreFlight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }
    
    /**
     * Vérifier la méthode HTTP
     */
    public static boolean isMethodAllowed(HttpExchange exchange, String... allowedMethods) {
        String method = exchange.getRequestMethod();
        for (String allowed : allowedMethods) {
            if (allowed.equals(method)) {
                return true;
            }
        }
        return false;
    }
}
