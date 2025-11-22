// src/main/java/com/skybooking/database/repositories/PaymentRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 💳 Repository pour la gestion des paiements
 */
public class PaymentRepository extends BaseRepository {
    
    public PaymentRepository() {
        super(Constants.COLLECTION_PAYMENTS);
    }
    
    /**
     * Trouver un paiement par ID
     */
    public Document findById(String paymentId) {
        return collection.find(Filters.eq("paymentId", paymentId)).first();
    }
    
    /**
     * Trouver les paiements d'un client
     */
    public List<Document> findByCustomerId(String customerId) {
        return collection.find(Filters.eq("customerId", customerId))
                        .sort(Sorts.descending("createdAt"))
                        .into(new ArrayList<>());
    }
    
    /**
     * Insérer un paiement
     */
    public void insertPayment(Document payment) {
        if (!payment.containsKey("createdAt")) {
            payment.append("createdAt", new Date());
        }
        insert(payment);
    }
    
    /**
     * Mettre à jour le statut d'un paiement
     */
    public void updateStatus(String paymentId, String status) {
        collection.updateOne(
            Filters.eq("paymentId", paymentId),
            Updates.combine(
                Updates.set("status", status),
                Updates.set("updatedAt", new Date())
            )
        );
    }
    
    /**
     * Vérifier si une réservation est payée
     */
    public boolean isReservationPaid(String reservationId) {
        return collection.countDocuments(
            Filters.and(
                Filters.eq("reservationId", reservationId),
                Filters.eq("status", "COMPLETED")
            )
        ) > 0;
    }
    
    /**
     * Obtenir les paiements par plage de dates
     */
    public List<Document> getPaymentsByDateRange(String startDate, String endDate) {
        return collection.find(
            Filters.and(
                Filters.gte("paymentDate", startDate),
                Filters.lte("paymentDate", endDate)
            )
        ).into(new ArrayList<>());
    }
    
    /**
     * Calculer le revenu total
     */
    public double calculateTotalRevenue() {
        List<Document> result = collection.aggregate(Arrays.asList(
            new Document("$match", new Document("status", "COMPLETED")),
            new Document("$group", new Document("_id", null)
                .append("total", new Document("$sum", "$amount")))
        )).into(new ArrayList<>());
        
        return result.isEmpty() ? 0.0 : result.get(0).getDouble("total");
    }
    
    /**
     * Compter les paiements en attente
     */
    public long countPendingPayments() {
        return collection.countDocuments(Filters.eq("status", "PENDING"));
    }
}