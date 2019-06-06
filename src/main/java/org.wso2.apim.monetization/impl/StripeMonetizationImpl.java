package org.wso2.apim.monetization.impl;

import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.Monetization;

import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;

import java.util.Map;

public class StripeMonetizationImpl implements Monetization {

    public boolean createBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    public boolean updateBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    public boolean deleteBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    public boolean enableMonetization(String s, API api, Map<String, String> map) throws MonetizationException {
        return false;
    }

    public boolean disableMonetization(String s, API api, Map<String, String> map) throws MonetizationException {
        return false;
    }

    public Map<String, String> getMonetizedPoliciesToPlanMapping(API api) throws MonetizationException {
        return null;
    }

    public Map<String, String> getCurrentUsage(String s, APIProvider apiProvider) throws MonetizationException {
        return null;
    }
}
