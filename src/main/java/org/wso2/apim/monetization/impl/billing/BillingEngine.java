package org.wso2.apim.monetization.impl.billing;


import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.apim.monetization.impl.model.billing.Subscription;
import org.wso2.carbon.apimgt.api.MonetizationException;

/**
 * Interface for a billing engine
 */
public interface BillingEngine {

    /**
     * Creates a customer in the billing engine
     *
     * @param customer The customer to create
     * @return The created customer
     * @throws MonetizationException if an error occurs
     */
    Customer createCustomer(Customer customer) throws MonetizationException;

    /**
     * Creates a subscription in the billing engine
     *
     * @param customer The customer to create the subscription for
     * @param priceId  The ID of the price to subscribe to
     * @return The created subscription
     * @throws MonetizationException if an error occurs
     */
    Subscription createSubscription(Customer customer, String priceId) throws MonetizationException;

}
