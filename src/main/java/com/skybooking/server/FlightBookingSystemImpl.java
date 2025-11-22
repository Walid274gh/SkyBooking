// src/main/java/com/skybooking/server/FlightBookingSystemImpl.java

package com.skybooking.server;

import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import FlightReservation.*;
import com.skybooking.managers.impl.*;

/**
 * 🎯 Implémentation du système principal
 */
public class FlightBookingSystemImpl extends FlightBookingSystemPOA {
    
    private CustomerManager customerManager;
    private FlightManager flightManager;
    private ReservationManager reservationManager;
    private PaymentManager paymentManager;
    private AccountManager accountManager;
    private CancellationManager cancellationManager;
    private AdminManager adminManager;
    
    public FlightBookingSystemImpl(ORB orb, POA poa) throws Exception {
        // Créer les implémentations
        CustomerManagerImpl customerMgrImpl = new CustomerManagerImpl();
        FlightManagerImpl flightMgrImpl = new FlightManagerImpl();
        ReservationManagerImpl reservationMgrImpl = new ReservationManagerImpl(flightMgrImpl);
        PaymentManagerImpl paymentMgrImpl = new PaymentManagerImpl();
        AccountManagerImpl accountMgrImpl = new AccountManagerImpl();
        CancellationManagerImpl cancellationMgrImpl = new CancellationManagerImpl();
        AdminManagerImpl adminMgrImpl = new AdminManagerImpl();
        
        // Activer les objets dans le POA
        byte[] custId = poa.activate_object(customerMgrImpl);
        byte[] flightId = poa.activate_object(flightMgrImpl);
        byte[] resId = poa.activate_object(reservationMgrImpl);
        byte[] paymentId = poa.activate_object(paymentMgrImpl);
        byte[] accountId = poa.activate_object(accountMgrImpl);
        byte[] cancellationId = poa.activate_object(cancellationMgrImpl);
        byte[] adminId = poa.activate_object(adminMgrImpl);
        
        // Obtenir les références
        customerManager = CustomerManagerHelper.narrow(poa.id_to_reference(custId));
        flightManager = FlightManagerHelper.narrow(poa.id_to_reference(flightId));
        reservationManager = ReservationManagerHelper.narrow(poa.id_to_reference(resId));
        paymentManager = PaymentManagerHelper.narrow(poa.id_to_reference(paymentId));
        accountManager = AccountManagerHelper.narrow(poa.id_to_reference(accountId));
        cancellationManager = CancellationManagerHelper.narrow(poa.id_to_reference(cancellationId));
        adminManager = AdminManagerHelper.narrow(poa.id_to_reference(adminId));
        
        System.out.println("✅ Tous les managers initialisés");
    }
    
    @Override
    public CustomerManager getCustomerManager() {
        return customerManager;
    }
    
    @Override
    public FlightManager getFlightManager() {
        return flightManager;
    }
    
    @Override
    public ReservationManager getReservationManager() {
        return reservationManager;
    }
    
    @Override
    public PaymentManager getPaymentManager() {
        return paymentManager;
    }
    
    @Override
    public AccountManager getAccountManager() {
        return accountManager;
    }
    
    @Override
    public CancellationManager getCancellationManager() {
        return cancellationManager;
    }
    
    @Override
    public AdminManager getAdminManager() {
        return adminManager;
    }
}