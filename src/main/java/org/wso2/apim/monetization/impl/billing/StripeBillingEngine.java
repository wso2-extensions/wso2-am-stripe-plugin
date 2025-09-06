package org.wso2.apim.monetization.impl.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceCreatePreviewParams;
import com.stripe.param.SubscriptionCreateParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.StripeMonetizationException;
import org.wso2.apim.monetization.impl.model.MonetizedStripeSubscriptionInfo;
import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.apim.monetization.impl.model.billing.Subscription;
import org.wso2.carbon.apimgt.api.MonetizationException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class StripeBillingEngine implements BillingEngine {

    private static final Log log = LogFactory.getLog(StripeBillingEngine.class);

    @Override
    public Customer createCustomer(Customer customer) throws MonetizationException {
        try {
            // Build customer creation parameters
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(customer.getName())
                    .build();

            // Create customer in Stripe
            com.stripe.model.Customer stripeCustomer = com.stripe.model.Customer.create(params);

            if (log.isDebugEnabled()) {
                log.debug("Stripe customer creation request: " + params);
                log.debug("Stripe customer creation response: " + stripeCustomer);
            }
            log.info("Stripe customer created successfully for subscriber: "
                    + customer.getName());
            customer.setId(stripeCustomer.getId());
            return customer;

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Stripe customer for subscriber [%s]",
                    customer.getName());
            log.error(errorMessage, e);
            throw new StripeMonetizationException(errorMessage, e);
        }
    }

    @Override
    public Subscription createSubscription(Customer customer, String priceId) throws MonetizationException {
        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .build()
                    ).build();
            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.create(params);
            Subscription subscription = new Subscription();
            subscription.setId(stripeSubscription.getId());
            return subscription;
        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating subscription for customer: " + customer.getId() + " and price: " + priceId);
            throw new StripeMonetizationException(errorMessage, e);
        }
    }

}
