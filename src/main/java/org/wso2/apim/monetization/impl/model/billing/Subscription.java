package org.wso2.apim.monetization.impl.model.billing;

/**
 * Represents a generic subscription in the billing system
 */
public class Subscription {

    private String id;
    private String customerId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
