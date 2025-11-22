// src/main/java/com/skybooking/rest/handlers/account/NewsletterHandler.java

package com.skybooking.rest.handlers.account;

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
 * 📧 Handler pour gérer les abonnements newsletter
 */
public class NewsletterHandler implements HttpHandler {
    
    private final AccountManager accountManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public NewsletterHandler(AccountManager accountManager,
                            TimeoutExecutor timeoutExecutor) {
        this.accountManager = accountManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        String method = exchange.getRequestMethod();
        
        if ("POST".equals(method)) {
            handleSubscribe(exchange);
        } else if ("DELETE".equals(method)) {
            handleUnsubscribe(exchange);
        } else {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
        }
    }
    
    private void handleSubscribe(HttpExchange exchange) throws IOException {
        try {
            System.out.println("→ Requête SUBSCRIBE NEWSLETTER reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            if (!json.has("email")) {
                ResponseHelper.sendError(exchange, 400, "Email manquant");
                return;
            }
            
            String email = json.get("email").getAsString();
            String customerId = json.has("customerId") 
                ? json.get("customerId").getAsString() 
                : "";
            
            System.out.println("  Email: " + email);
            
            // Créer l'objet Newsletter
            Newsletter newsletter = new Newsletter(
                email,
                customerId,
                true,
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date())
            );
            
            // Appel CORBA avec timeout
            boolean subscribed = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.subscribeNewsletter(newsletter);
            }, Constants.TIMEOUT_DEFAULT, "abonnement newsletter");
            
            if (subscribed) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Abonnement newsletter réussi");
                response.addProperty("email", email);
                
                System.out.println("✅ Newsletter souscrite: " + email);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, 
                    "Échec de l'abonnement (email déjà abonné)");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur abonnement newsletter: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
    
    private void handleUnsubscribe(HttpExchange exchange) throws IOException {
        try {
            System.out.println("→ Requête UNSUBSCRIBE NEWSLETTER reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null || !json.has("email")) {
                ResponseHelper.sendError(exchange, 400, "Email manquant");
                return;
            }
            
            String email = json.get("email").getAsString();
            
            System.out.println("  Email: " + email);
            
            // Appel CORBA avec timeout
            boolean unsubscribed = timeoutExecutor.executeWithTimeout(() -> {
                return accountManager.unsubscribeNewsletter(email);
            }, Constants.TIMEOUT_DEFAULT, "désabonnement newsletter");
            
            if (unsubscribed) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Désabonnement newsletter réussi");
                response.addProperty("email", email);
                
                System.out.println("✅ Newsletter désabonnée: " + email);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 404, "Email non trouvé");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur désabonnement newsletter: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
