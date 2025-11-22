// src/main/java/com/skybooking/rest/middleware/TimeoutExecutor.java

package com.skybooking.rest.middleware;

import java.util.concurrent.*;

/**
 * ⏱️ Executor avec timeout pour limiter les appels CORBA
 */
public class TimeoutExecutor {
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    /**
     * Exécuter une tâche avec timeout
     */
    public <T> T executeWithTimeout(Callable<T> task, long timeoutSeconds, String operationName) 
            throws Exception {
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
            
        } catch (TimeoutException e) {
            future.cancel(true);
            System.err.println("⏱ TIMEOUT: " + operationName + " (" + timeoutSeconds + "s)");
            throw new Exception("L'opération " + operationName + 
                              " a pris trop de temps. Le serveur CORBA ne répond pas.");
                              
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new Exception("Erreur lors de " + operationName + ": " + 
                              cause.getMessage());
                              
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Opération " + operationName + " interrompue");
        }
    }
    
    /**
     * Arrêter l'executor proprement
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Vérifier si l'executor est arrêté
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }
}