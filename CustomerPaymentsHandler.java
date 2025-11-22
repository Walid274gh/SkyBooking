// src/main/java/com/skybooking/rest/handlers/payment/CustomerPaymentsHandler.java

package com.skybooking.rest.handlers.payment;

import com.google.gson.JsonArray;
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
 * 💳 Handler pour récupérer les paiements d'un client
 */
public class CustomerPaymentsHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public CustomerPaymentsHandler(PaymentManager paymentManager,
                                  TimeoutExecutor timeoutExecutor) {
        this.paymentManager = paymentManager;
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
            String customerId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (customerId == null || customerId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID client invalide");
                return;
            }
            
            System.out.println("→ Requête GET CUSTOMER PAYMENTS: " + customerId);
            
            // Appel CORBA avec timeout
            Payment[] payments = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getCustomerPayments(customerId);
            }, Constants.TIMEOUT_DEFAULT, "récupération paiements client");
            
            JsonArray paymentsArray = new JsonArray();
            for (Payment payment : payments) {
                paymentsArray.add(JsonHelper.paymentToJson(payment));
            }
            
            JsonObject response = new JsonObject();
            response.add("payments", paymentsArray);
            response.addProperty("count", payments.length);
            
            System.out.println("✅ " + payments.length + " paiement(s) trouvé(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération paiements: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}