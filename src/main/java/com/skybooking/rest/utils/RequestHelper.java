// src/main/java/com/skybooking/rest/utils/RequestHelper.java

package com.skybooking.rest.utils;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 📥 Utilitaires pour traiter les requêtes HTTP
 */
public class RequestHelper {
    
    /**
     * Lire le corps de la requête
     */
    public static String readRequestBody(HttpExchange exchange) throws IOException {
    try (InputStream is = exchange.getRequestBody();
         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        
        byte[] buffer = new byte[4096];
        int bytesRead;
        
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toString(StandardCharsets.UTF_8.name());
       }
    }
    
    /**
     * Parser les paramètres de query string
     */
    public static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getQuery();
        
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    try {
                        params.put(
                            URLDecoder.decode(pair[0], "UTF-8"),
                            URLDecoder.decode(pair[1], "UTF-8")
                        );
                    } catch (UnsupportedEncodingException e) {
                        System.err.println("❌ Erreur décodage paramètre: " + e.getMessage());
                    }
                }
            }
        }
        
        return params;
    }
    
    /**
     * Extraire un paramètre de chemin
     */
    public static String extractPathParameter(String path, int index) {
        String[] parts = path.split("/");
        if (parts.length > index) {
            String param = parts[index];
            if (param != null && !param.isEmpty()) {
                return param;
            }
        }
        return null;
    }
    
    /**
     * Obtenir l'IP du client
     */
    public static String getClientIP(HttpExchange exchange) {
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }
    
    /**
     * Obtenir le User-Agent
     */
    public static String getUserAgent(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("User-Agent");
    }
    
    /**
     * Vérifier si la requête contient du JSON
     */
    public static boolean isJsonRequest(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        return contentType != null && contentType.contains("application/json");
    }
}