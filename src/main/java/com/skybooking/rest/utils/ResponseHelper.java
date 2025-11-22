// src/main/java/com/skybooking/rest/utils/ResponseHelper.java

package com.skybooking.rest.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 📤 Utilitaires pour envoyer des réponses HTTP
 */
public class ResponseHelper {
    
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();
    
    /**
     * Envoyer une réponse JSON
     */
    public static void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) 
            throws IOException {
        try {
            String jsonResponse = GSON.toJson(data);
            byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur envoi réponse: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Envoyer une erreur JSON
     */
    public static void sendError(HttpExchange exchange, int statusCode, String message) 
            throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        error.addProperty("timestamp", System.currentTimeMillis());
        
        sendJsonResponse(exchange, statusCode, error);
    }
    
    /**
     * Envoyer un succès simple
     */
    public static void sendSuccess(HttpExchange exchange, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", message);
        
        sendJsonResponse(exchange, 200, response);
    }
    
    /**
     * Envoyer des données binaires (PDF, images)
     */
    public static void sendBinaryResponse(HttpExchange exchange, byte[] data, 
                                         String contentType, String filename) 
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Content-Disposition", 
            "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(data.length));
        
        exchange.sendResponseHeaders(200, data.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
    
    /**
     * Envoyer une réponse vide (204 No Content)
     */
    public static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}