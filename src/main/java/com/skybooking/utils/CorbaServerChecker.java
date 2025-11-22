// Vérifie si le serveur CORBA est enregistré
// ==========================================
package com.skybooking.utils;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import com.skybooking.utils.Constants;

public class CorbaServerChecker {
    private static final String CORBA_SERVER_NAME = com.skybooking.utils.Constants.CORBA_SERVICE_NAME;
    
    public static void main(String[] args) {
        try {
            Properties props = new Properties();
            props.put(Context.INITIAL_CONTEXT_FACTORY, 
                     "com.sun.jndi.cosnaming.CNCtxFactory");
            props.put(Context.PROVIDER_URL, 
                     System.getProperty("java.naming.provider.url", "iiop://localhost:1050"));
            
            Context ctx = new InitialContext(props);
            
            // Tenter de récupérer le serveur CORBA
            Object obj = ctx.lookup(CORBA_SERVER_NAME);
            
            if (obj != null) {
                ctx.close();
                System.out.println("OK");
                System.exit(0);
            } else {
                throw new NamingException("Server not found");
            }
            
        } catch (NamingException e) {
            System.err.println("FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}