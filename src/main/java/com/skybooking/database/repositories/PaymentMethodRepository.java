// src/main/java/com/skybooking/database/repositories/PaymentMethodRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.*;

/**
 * 💳 Repository pour les méthodes de paiement sauvegardées
 */
public class PaymentMethodRepository extends BaseRepository {
    
    public PaymentMethodRepository() {
        super(Constants.COLLECTION_PAYMENT_METHODS);
    }
    
    /**
     * Trouver par ID
     */
    public Document findById(String paymentMethodId) {
        return collection.find(Filters.eq("paymentMethodId", paymentMethodId)).first();
    }
    
    /**
     * Trouver les méthodes d'un client
     */
    public List<Document> findByCustomerId(String customerId) {
        return collection.find(Filters.eq("customerId", customerId))
                        .into(new ArrayList<>());
    }
    
    /**
     * Insérer une méthode de paiement
     */
    public void insertPaymentMethod(Document paymentMethod) {
        if (!paymentMethod.containsKey("createdAt")) {
            paymentMethod.append("createdAt", new Date());
        }
        insert(paymentMethod);
    }
    
    /**
     * Supprimer une méthode
     */
    public boolean deletePaymentMethod(String paymentMethodId) {
        return collection.deleteOne(Filters.eq("paymentMethodId", paymentMethodId))
                        .getDeletedCount() > 0;
    }
    
    /**
     * Retirer le flag par défaut des autres méthodes
     */
    public void unsetDefaultPaymentMethods(String customerId, String excludeId) {
        collection.updateMany(
            Filters.and(
                Filters.eq("customerId", customerId),
                Filters.ne("paymentMethodId", excludeId)
            ),
            Updates.set("isDefault", false)
        );
    }
    
    /**
     * Définir une méthode par défaut
     */
    public void setPaymentMethodDefault(String paymentMethodId, boolean isDefault) {
        collection.updateOne(
            Filters.eq("paymentMethodId", paymentMethodId),
            Updates.set("isDefault", isDefault)
        );
    }
}