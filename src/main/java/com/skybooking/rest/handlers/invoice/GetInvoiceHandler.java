// src/main/java/com/skybooking/rest/handlers/invoice/GetInvoiceHandler.java

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
 * 🧾 Handler pour récupérer une facture par ID
 */
public class GetInvoiceHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public GetInvoiceHandler(PaymentManager paymentManager,
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
            String invoiceId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 3
            );
            
            if (invoiceId == null || invoiceId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID facture invalide");
                return;
            }
            
            System.out.println("→ Requête GET INVOICE: " + invoiceId);
            
            // Appel CORBA avec timeout
            Invoice invoice = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getInvoice(invoiceId);
            }, Constants.TIMEOUT_DEFAULT, "récupération facture");
            
            if (invoice == null) {
                ResponseHelper.sendError(exchange, 404, "Facture non trouvée");
                return;
            }
            
            JsonObject response = JsonHelper.invoiceToJson(invoice);
            
            System.out.println("✅ Facture trouvée: " + invoiceId);
            ResponseHelper.sendJsonResponse(exchange, 200, response);
            
        } catch (Exception e) {
            System.err.println("✗ Erreur récupération facture: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}