// src/main/java/com/skybooking/rest/handlers/download/DownloadInvoiceHandler.java

package com.skybooking.rest.handlers.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.pdf.InvoicePDFGenerator;
import FlightReservation.*;
import java.io.IOException;

/**
 * 📥 Handler pour télécharger une facture en PDF
 */
public class DownloadInvoiceHandler implements HttpHandler {
    
    private final PaymentManager paymentManager;
    private final ReservationManager reservationManager;
    private final FlightManager flightManager;
    private final CustomerManager customerManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public DownloadInvoiceHandler(PaymentManager paymentManager,
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
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "GET")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            String invoiceId = RequestHelper.extractPathParameter(
                exchange.getRequestURI().getPath(), 4
            );
            
            if (invoiceId == null || invoiceId.isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "ID de facture invalide");
                return;
            }
            
            System.out.println("→ Requête DOWNLOAD INVOICE PDF: " + invoiceId);
            
            // Récupérer les données
            Invoice invoice = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getInvoice(invoiceId);
            }, 10, "récupération facture");
            
            if (invoice == null) {
                ResponseHelper.sendError(exchange, 404, "Facture non trouvée");
                return;
            }
            
            Payment payment = timeoutExecutor.executeWithTimeout(() -> {
                return paymentManager.getPayment(invoice.paymentId);
            }, 10, "récupération paiement");
            
            Reservation reservation = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.getReservation(invoice.reservationId);
            }, 10, "récupération réservation");
            
            Flight flight = timeoutExecutor.executeWithTimeout(() -> {
                return flightManager.getFlightById(reservation.flightId);
            }, 10, "récupération vol");
            
            Customer customer = timeoutExecutor.executeWithTimeout(() -> {
                return customerManager.getCustomerById(reservation.customerId);
            }, 10, "récupération client");
            
            // Générer le PDF
            byte[] pdfBytes = InvoicePDFGenerator.generateInvoicePDF(
                invoice, payment, reservation, customer, flight
            );
            
            // Envoyer le PDF
            String filename = String.format("facture_%s.pdf", invoiceId);
            
            ResponseHelper.sendBinaryResponse(
                exchange, 
                pdfBytes, 
                "application/pdf", 
                filename
            );
            
            System.out.println("✅ PDF facture envoyé: " + filename);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur génération PDF facture: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
