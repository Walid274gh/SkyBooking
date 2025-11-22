// src/main/java/com/skybooking/server/FlightBookingServer.java

package com.skybooking.server;

import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import org.omg.PortableServer.*;
import FlightReservation.*;
import com.skybooking.managers.impl.*;
import com.skybooking.database.MongoDBConnector;
import com.skybooking.utils.Constants;

/**
 * 🚀 SERVEUR PRINCIPAL CORBA
 * Gère le système de réservation de vols
 */
public class FlightBookingServer {
    
    public static void main(String[] args) {
        try {
            System.out.println("===========================================");
            System.out.println("🇩🇿 SERVEUR CORBA - SKYBOOKING ALGÉRIE");
            System.out.println("===========================================");
            
            // Initialiser MongoDB
            MongoDBConnector db = MongoDBConnector.getInstance();
            db.printDatabaseStats();
            
            // Initialiser CORBA
            ORB orb = ORB.init(args, null);
            System.out.println("✓ ORB initialisé");
            
            POA rootPOA = POAHelper.narrow(
                orb.resolve_initial_references("RootPOA"));
            rootPOA.the_POAManager().activate();
            System.out.println("✓ POA activé");
            
            // Créer le système de réservation
            FlightBookingSystemImpl system = 
                new FlightBookingSystemImpl(orb, rootPOA);
            
            byte[] id = rootPOA.activate_object(system);
            FlightBookingSystem ref = FlightBookingSystemHelper.narrow(
                rootPOA.id_to_reference(id));
            System.out.println("✓ Système de réservation créé");
            
            // Enregistrer dans le naming service
            org.omg.CORBA.Object objRef = 
                orb.resolve_initial_references(Constants.NAMING_SERVICE);
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            
            NameComponent[] path = ncRef.to_name(Constants.CORBA_SERVICE_NAME);
            ncRef.rebind(path, ref);
            System.out.println("✓ Service enregistré dans le naming service");
            
            System.out.println("===========================================");
            System.out.println("✅ SERVEUR PRÊT AVEC MONGODB + BCRYPT");
            System.out.println("Base de données : MongoDB");
            System.out.println("Sécurité : Mots de passe cryptés avec BCrypt");
            System.out.println("⭐ Améliorations:");
            System.out.println("  - Réservation atomique sans race condition");
            System.out.println("  - Rollback complet en cas d'erreur");
            System.out.println("  - Validation des dates de vol");
            System.out.println("  - Architecture modulaire professionnelle");
            System.out.println("En attente de connexions clients...");
            System.out.println("===========================================");
            
            // Hook pour fermer proprement MongoDB
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n→ Arrêt du serveur...");
                db.close();
                System.out.println("✓ Serveur arrêté proprement");
            }));
            
            orb.run();
            
        } catch (Exception e) {
            System.err.println("✗ ERREUR FATALE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
