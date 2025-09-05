package org.wso2.apim.monetization.impl;

import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.Monetization;

public interface BillingProvider{
    void initializeBillingProvider(Monetization monetization) throws MonetizationException;

    String createSubscription(String subscriberId, String priceId) throws MonetizationException;
}
