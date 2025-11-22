// src/main/java/com/skybooking/database/repositories/InvoiceRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 🧾 Repository pour la gestion des factures
 */
public class InvoiceRepository extends BaseRepository {
    
    public InvoiceRepository() {
        super(Constants.COLLECTION_INVOICES);
    }
    
    /**
     * Trouver une facture par ID
     */
    public Document findById(String invoiceId) {
        return collection.find(Filters.eq("invoiceId", invoiceId)).first();
    }
    
    /**
     * Trouver les factures d'un client
     */
    public List<Document> findByCustomerId(String customerId, PaymentRepository paymentRepo) {
        // Récupérer via les paiements
        List<Document> payments = paymentRepo.findByCustomerId(customerId);
        List<String> paymentIds = new ArrayList<>();
        
        for (Document payment : payments) {
            paymentIds.add(payment.getString("paymentId"));
        }
        
        if (paymentIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return collection.find(Filters.in("paymentId", paymentIds))
                        .into(new ArrayList<>());
    }
    
    /**
     * Insérer une facture
     */
    public void insertInvoice(Document invoice) {
        if (!invoice.containsKey("createdAt")) {
            invoice.append("createdAt", new Date());
        }
        insert(invoice);
    }
}