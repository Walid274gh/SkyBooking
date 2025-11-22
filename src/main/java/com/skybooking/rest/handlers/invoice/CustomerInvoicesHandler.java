// src/main/java/com/skybooking/rest/handlers/invoice/CustomerInvoicesHandler.java

package com.skybooking.rest.handlers.invoice;

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
 * 🧾 Handler pour récupérer les factures d'un client
 */
public class CustomerInvoicesHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public CustomerInvoicesHandler(PaymentManager paymentManager,
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
            
            System.out.println("→ Requête GET CUSTOMER INVOICES: " + customerId);
            
            // Appel CORBA avec timeout
            Invoice[] invoices = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getCustomerInvoices(customerId);
            }, Constants.TIMEOUT_DEFAULT, "récupération factures client");
            
            JsonArray invoicesArray = new JsonArray();
            for (Invoice invoice : invoices) {
                invoicesArray.add(JsonHelper.invoiceToJson(invoice));
            }
            
            JsonObject response = new JsonObject();
            response.add("invoices", invoicesArray);
            response.addProperty("count", invoices.length);
            
            System.out.println("✅ " + invoices.length + " facture(s) trouvée(s)");
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération factures: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
