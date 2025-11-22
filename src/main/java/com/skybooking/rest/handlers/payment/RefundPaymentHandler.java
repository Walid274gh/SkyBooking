// src/main/java/com/skybooking/rest/handlers/payment/RefundPaymentHandler.java

package com.skybooking.rest.handlers.payment;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 💰 Handler pour rembourser un paiement
 */
public class RefundPaymentHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public RefundPaymentHandler(PaymentManager paymentManager,
                               TimeoutExecutor timeoutExecutor) {
        this.paymentManager = paymentManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String paymentId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (paymentId == null || paymentId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID paiement invalide");
                return;
            }
            
            System.out.println("→ Requête REFUND PAYMENT: " + paymentId);
            
            // Appel CORBA avec timeout
            boolean refunded = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.refundPayment(paymentId);
            }, Constants.TIMEOUT_PAYMENT, "remboursement paiement");
            
            if (refunded) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Remboursement effectué avec succès");
                response.addProperty("paymentId", paymentId);
                
                System.out.println("✅ Paiement remboursé: " + paymentId);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, "Échec du remboursement");
            }
            
        } catch (RefundException e) {
            System.err.println("✗ Erreur remboursement: " + e.message);
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (Exception e) {
            System.err.println("✗ Erreur serveur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}