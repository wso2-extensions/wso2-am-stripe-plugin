/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apim.monetization.impl.billing;

import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.StripeMonetizationException;
import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.apim.monetization.impl.model.billing.SubscriptionInfo;
import org.wso2.carbon.apimgt.api.MonetizationException;

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
    public SubscriptionInfo createSubscription(Customer customer, String priceId) throws MonetizationException {
        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .build()
                    ).build();
            com.stripe.model.Subscription stripeSubscription = com.stripe.model.Subscription.create(params);
            SubscriptionInfo subscription = new SubscriptionInfo();
            subscription.setId(stripeSubscription.getId());
            return subscription;
        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating subscription for customer: " + customer.getId() + " and price: " + priceId);
            throw new StripeMonetizationException(errorMessage, e);
        }
    }

}
