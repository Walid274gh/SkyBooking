// src/main/java/com/skybooking/database/repositories/AdminRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import com.skybooking.utils.Constants;
import java.util.Date;

/**
 * 👨‍💼 Repository pour la gestion des administrateurs
 */
public class AdminRepository extends BaseRepository {
    
    public AdminRepository() {
        super(Constants.COLLECTION_ADMINS);
        createIndexes();
    }
    
    private void createIndexes() {
        collection.createIndex(
            Indexes.ascending("username"), 
            new IndexOptions().unique(true)
        );
    }
    
    /**
     * Trouver un admin par username
     */
    public Document findByUsername(String username) {
        return collection.find(Filters.eq("username", username)).first();
    }
    
    /**
     * Trouver un admin par ID
     */
    public Document findById(String adminId) {
        return collection.find(Filters.eq("adminId", adminId)).first();
    }
    
    /**
     * Insérer un nouvel admin
     */
    public void insertAdmin(Document admin) {
        if (!admin.containsKey("createdAt")) {
            admin.append("createdAt", new Date());
        }
        insert(admin);
    }
    
    /**
     * Vérifier si un admin existe
     */
    public boolean adminExists(String username) {
        return collection.countDocuments(Filters.eq("username", username)) > 0;
    }
}
