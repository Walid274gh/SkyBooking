// src/main/java/com/skybooking/rest/handlers/payment/ProcessPaymentHandler.java

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
import com.skybooking.utils.ValidationUtils;
import FlightReservation.*;
import java.io.IOException;

/**
 * 💳 Handler pour traiter un paiement
 */
public class ProcessPaymentHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public ProcessPaymentHandler(PaymentManager paymentManager,
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
            System.out.println("→ Requête PROCESS PAYMENT reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            String[] requiredFields = {"reservationId", "customerId", "amount", 
                                       "method", "cardNumber", "cardHolder", 
                                       "expiryDate", "cvv"};
            for (String field : requiredFields) {
                if (!json.has(field)) {
                    ResponseHelper.sendError(exchange, 400, 
                        "Paramètre manquant: " + field);
                    return;
                }
            }
            
            String reservationId = json.get("reservationId").getAsString();
            String customerId = json.get("customerId").getAsString();
            double amount = json.get("amount").getAsDouble();
            String method = json.get("method").getAsString();
            String cardNumber = json.get("cardNumber").getAsString();
            String cardHolder = json.get("cardHolder").getAsString();
            String expiryDate = json.get("expiryDate").getAsString();
            String cvv = json.get("cvv").getAsString();
            
            // Validations
            if (!ValidationUtils.isValidAmount(amount)) {
                ResponseHelper.sendError(exchange, 400, "Montant invalide");
                return;
            }
            
            if (!ValidationUtils.isValidCardNumber(cardNumber)) {
                ResponseHelper.sendError(exchange, 400, "Numéro de carte invalide");
                return;
            }
            
            if (!ValidationUtils.isValidCVV(cvv)) {
                ResponseHelper.sendError(exchange, 400, "CVV invalide");
                return;
            }
            
            if (!ValidationUtils.isValidExpiryDate(expiryDate)) {
                ResponseHelper.sendError(exchange, 400, "Date d'expiration invalide");
                return;
            }
            
            PaymentMethod paymentMethod = "CIB".equals(method) 
                ? PaymentMethod.CIB 
                : PaymentMethod.EDAHABIA;
            
            System.out.println("  Réservation: " + reservationId);
            System.out.println("  Montant: " + amount + " DZD");
            System.out.println("  Méthode: " + method);
            
            // Appel CORBA avec timeout
            Payment payment = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.processPayment(
                    reservationId, customerId, amount, paymentMethod,
                    cardNumber, cardHolder, expiryDate, cvv
                );
            }, Constants.TIMEOUT_PAYMENT, "traitement paiement");
            
            JsonObject response = JsonHelper.paymentToJson(payment);
            System.out.println("✅ Paiement traité: " + payment.paymentId);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (InsufficientFundsException e) {
            System.err.println("❌ Fonds insuffisants: " + e.message);
            ResponseHelper.sendError(exchange, 402, e.message);
        } catch (InvalidCardException e) {
            System.err.println("❌ Carte invalide: " + e.message);
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (PaymentException e) {
            System.err.println("❌ Erreur paiement: " + e.message);
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
