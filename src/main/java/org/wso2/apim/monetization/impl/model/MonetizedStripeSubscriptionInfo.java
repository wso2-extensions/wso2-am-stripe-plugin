package org.wso2.apim.monetization.impl.model;

public class MonetizedStripeSubscriptionInfo {
    private String subscriptionId;
    private String customerId;

    public MonetizedStripeSubscriptionInfo(String subscriptionId, String customerId) {
        this.subscriptionId = subscriptionId;
        this.customerId = customerId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
