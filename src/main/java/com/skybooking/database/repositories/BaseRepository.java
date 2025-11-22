// src/main/java/com/skybooking/database/repositories/BaseRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import com.skybooking.database.MongoDBConnector;

/**
 * 🗄️ Repository de base avec méthodes communes
 */
public abstract class BaseRepository {
    
    protected final MongoDatabase database;
    protected final MongoCollection<Document> collection;
    
    protected BaseRepository(String collectionName) {
        this.database = MongoDBConnector.getInstance().getDatabase();
        this.collection = database.getCollection(collectionName);
    }
    
    /**
     * Compter les documents
     */
    public long count() {
        return collection.countDocuments();
    }
    
    /**
     * Insérer un document
     */
    protected void insert(Document document) {
        collection.insertOne(document);
    }
    
    /**
     * Supprimer tous les documents
     */
    public void deleteAll() {
        collection.deleteMany(new Document());
    }
}