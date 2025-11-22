// src/main/java/com/skybooking/database/repositories/RefundRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.Date;

/**
 * 💰 Repository pour les remboursements
 */
public class RefundRepository extends BaseRepository {
    
    public RefundRepository() {
        super(Constants.COLLECTION_REFUNDS);
    }
    
    /**
     * Trouver un remboursement par réservation
     */
    public Document findByReservationId(String reservationId) {
        return collection.find(Filters.eq("reservationId", reservationId)).first();
    }
    
    /**
     * Insérer un remboursement
     */
    public void insertRefund(Document refund) {
        if (!refund.containsKey("createdAt")) {
            refund.append("createdAt", new Date());
        }
        insert(refund);
    }
}