// src/main/java/com/skybooking/rest/handlers/payment/GetPaymentHandler.java

package com.skybooking.rest.handlers.payment;

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
 * 💳 Handler pour récupérer un paiement par ID
 */
public class GetPaymentHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public GetPaymentHandler(PaymentManager paymentManager,
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
            String paymentId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 3
            );
            
            if (paymentId == null || paymentId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID paiement invalide");
                return;
            }
            
            System.out.println("→ Requête GET PAYMENT: " + paymentId);
            
            // Appel CORBA avec timeout
            Payment payment = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getPayment(paymentId);
            }, Constants.TIMEOUT_DEFAULT, "récupération paiement");
            
            if (payment == null) {
                ResponseHelper.sendError(exchange, 404, "Paiement non trouvé");
                return;
            }
            
            JsonObject response = JsonHelper.paymentToJson(payment);
            
            System.out.println("✅ Paiement trouvé: " + paymentId);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération paiement: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}