/*
 *  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apim.monetization.impl.billing;


import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.apim.monetization.impl.model.billing.SubscriptionInfo;
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
    SubscriptionInfo createSubscription(Customer customer, String priceId) throws MonetizationException;

}
