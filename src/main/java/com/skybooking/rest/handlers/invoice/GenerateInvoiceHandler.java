// src/main/java/com/skybooking/rest/handlers/invoice/GenerateInvoiceHandler.java

package com.skybooking.rest.handlers.invoice;

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
 * 🧾 Handler pour générer une facture
 */
public class GenerateInvoiceHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final ReservationManager reservationManager;
    private final FlightManager flightManager;
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public GenerateInvoiceHandler(PaymentManager paymentManager,
                                 ReservationManager reservationManager,
                                 FlightManager flightManager,
                                 CustomerManager customerManager,
                                 TimeoutExecutor timeoutExecutor) {
        this.paymentManager = paymentManager;
        this.reservationManager = reservationManager;
        this.flightManager = flightManager;
        this.customerManager = customerManager;
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
            
            System.out.println("→ Requête GENERATE INVOICE: " + paymentId);
            
            // Appel CORBA avec timeout
            Invoice invoice = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.generateInvoice(paymentId);
            }, Constants.TIMEOUT_DEFAULT, "génération facture");
            
            if (invoice == null) {
                ResponseHelper.sendError(exchange, 404, "Impossible de générer la facture");
                return;
            }
            
            JsonObject response = JsonHelper.invoiceToJson(invoice);
            
            System.out.println("✅ Facture générée: " + invoice.invoiceId);
            ResponseHelper.sendJsonResponse(exchange, 201, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur génération facture: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}