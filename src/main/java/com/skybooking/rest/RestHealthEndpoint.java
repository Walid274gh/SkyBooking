// ==========================================
// RestHealthEndpoint.java - COMPLET
// Endpoint de santé réel pour Docker
// ==========================================
package com.skybooking.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.database.MongoDBConnector;
import org.json.JSONObject;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import com.skybooking.utils.Constants;

public class RestHealthEndpoint implements HttpHandler {
    
    private static final String CORBA_SERVER_NAME = com.skybooking.utils.Constants.CORBA_SERVICE_NAME;
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Accepter uniquement GET
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Vérifier CORBA
            boolean corbaHealthy = checkCorbaHealth();
            
            // Vérifier MongoDB
            boolean mongoHealthy = checkMongoHealth();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Construire la réponse JSON
            JSONObject response = new JSONObject();
            response.put("status", (corbaHealthy && mongoHealthy) ? "healthy" : "unhealthy");
            response.put("timestamp", System.currentTimeMillis());
            response.put("responseTime", responseTime + "ms");
            
            // Détails des services
            JSONObject services = new JSONObject();
            services.put("corba", corbaHealthy ? "up" : "down");
            services.put("mongodb", mongoHealthy ? "up" : "down");
            response.put("services", services);
            
            // Informations supplémentaires
            if (mongoHealthy) {
                try {
                    MongoDBConnector connector = MongoDBConnector.getInstance();
                    long customerCount = connector.getDatabase()
                        .getCollection("customers").countDocuments();
                    long flightCount = connector.getDatabase()
                        .getCollection("flights").countDocuments();
                    
                    JSONObject stats = new JSONObject();
                    stats.put("customers", customerCount);
                    stats.put("flights", flightCount);
                    response.put("database_stats", stats);
                } catch (Exception e) {
                    // Ignorer si impossible de récupérer les stats
                }
            }
            
            // Envoyer la réponse
            String jsonResponse = response.toString();
            byte[] responseBytes = jsonResponse.getBytes("UTF-8");
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            
            int statusCode = (corbaHealthy && mongoHealthy) ? 200 : 503;
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
            
            // Log
            System.out.println(
                String.format("[HEALTH] Status: %s, CORBA: %s, MongoDB: %s, Time: %dms",
                    response.getString("status"),
                    corbaHealthy ? "OK" : "FAIL",
                    mongoHealthy ? "OK" : "FAIL",
                    responseTime
                )
            );
            
        } catch (Exception e) {
            // En cas d'erreur critique
            sendErrorResponse(exchange, e);
        }
    }
    
    /**
     * ✅ Vérifier la santé de CORBA (RÉEL)
     */
    private boolean checkCorbaHealth() {
        try {
            // Configuration CORBA
            Properties props = new Properties();
            props.put(Context.INITIAL_CONTEXT_FACTORY, 
                     "com.sun.jndi.cosnaming.CNCtxFactory");
            
            String namingPort = System.getenv("CORBA_NAMING_PORT");
            if (namingPort == null) namingPort = "1050";
            
            props.put(Context.PROVIDER_URL, 
                     "iiop://localhost:" + namingPort);
            
            // Créer le contexte et vérifier le serveur
            Context ctx = new InitialContext(props);
            Object obj = ctx.lookup(CORBA_SERVER_NAME);
            ctx.close();
            
            // Si on arrive ici, CORBA est opérationnel
            return obj != null;
            
        } catch (Exception e) {
            System.err.println("[HEALTH] CORBA check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * ✅ Vérifier la santé de MongoDB (RÉEL)
     */
    private boolean checkMongoHealth() {
        try {
            // Utiliser la méthode existante de MongoDBConnector
            MongoDBConnector connector = MongoDBConnector.getInstance();
            return connector.checkHealth();
            
        } catch (Exception e) {
            System.err.println("[HEALTH] MongoDB check failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Envoyer une réponse d'erreur
     */
    private void sendErrorResponse(HttpExchange exchange, Exception e) throws IOException {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("status", "error");
        errorResponse.put("message", e.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        String jsonResponse = errorResponse.toString();
        byte[] responseBytes = jsonResponse.getBytes("UTF-8");
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(503, responseBytes.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
        
        System.err.println("[HEALTH] Error: " + e.getMessage());
    }
}