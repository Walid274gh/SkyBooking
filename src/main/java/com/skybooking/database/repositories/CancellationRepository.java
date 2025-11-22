// src/main/java/com/skybooking/database/repositories/CancellationRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.Date;

/**
 * ❌ Repository pour les annulations
 */
public class CancellationRepository extends BaseRepository {
    
    public CancellationRepository() {
        super(Constants.COLLECTION_CANCELLATIONS);
    }
    
    /**
     * Trouver une annulation par réservation
     */
    public Document findByReservationId(String reservationId) {
        return collection.find(Filters.eq("reservationId", reservationId)).first();
    }
    
    /**
     * Insérer une annulation
     */
    public void insertCancellation(Document cancellation) {
        if (!cancellation.containsKey("createdAt")) {
            cancellation.append("createdAt", new Date());
        }
        insert(cancellation);
    }
}