// src/main/java/com/skybooking/database/repositories/CustomerRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.skybooking.utils.Constants;
import java.util.Date;

/**
 * 👤 Repository pour la gestion des clients
 */
public class CustomerRepository extends BaseRepository {
    
    public CustomerRepository() {
        super(Constants.COLLECTION_CUSTOMERS);
    }
    
    /**
     * Trouver un client par username
     */
    public Document findByUsername(String username) {
        return collection.find(Filters.eq("username", username)).first();
    }
    
    /**
     * Trouver un client par ID
     */
    public Document findById(String customerId) {
        return collection.find(Filters.eq("customerId", customerId)).first();
    }
    
    /**
     * Trouver un client par email
     */
    public Document findByEmail(String email) {
        return collection.find(Filters.eq("email", email)).first();
    }
    
    /**
     * Vérifier si un username existe
     */
    public boolean usernameExists(String username) {
        return collection.countDocuments(Filters.eq("username", username)) > 0;
    }
    
    /**
     * Insérer un nouveau client
     */
    public void insertCustomer(Document customer) {
        if (!customer.containsKey("createdAt")) {
            customer.append("createdAt", new Date());
        }
        insert(customer);
    }
    
    /**
     * Mettre à jour un client
     */
    public void updateCustomer(String customerId, Document updates) {
        updates.append("updatedAt", new Date());
        collection.updateOne(
            Filters.eq("customerId", customerId),
            new Document("$set", updates)
        );
    }
    
    /**
     * Supprimer un client
     */
    public boolean deleteCustomer(String customerId) {
        return collection.deleteOne(Filters.eq("customerId", customerId))
                        .getDeletedCount() > 0;
    }
    
    /**
     * Obtenir tous les clients
     */
    public java.util.List<Document> getAllCustomers() {
        return collection.find().into(new java.util.ArrayList<>());
    }
}