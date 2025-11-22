// Vérifie si le Naming Service CORBA répond
// ==========================================
package com.skybooking.utils;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

public class NamingServiceChecker {
    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.put(Context.INITIAL_CONTEXT_FACTORY, 
                     "com.sun.jndi.cosnaming.CNCtxFactory");
            props.put(Context.PROVIDER_URL, 
                     System.getProperty("java.naming.provider.url", "iiop://localhost:1050"));
            
            // Tenter de créer un contexte
            Context ctx = new InitialContext(props);
            
            // Tenter de lister les bindings (simple vérification)
            ctx.list("");
            
            ctx.close();
            
            // Succès
            System.out.println("OK");
            System.exit(0);
            
        } catch (NamingException e) {
            // Échec
            System.err.println("FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}
