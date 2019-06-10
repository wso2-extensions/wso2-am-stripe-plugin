package org.wso2.apim.monetization.impl.model;

public class MonetizationPlatformCustomer {

    private int tenantId;
    private String customerId;
    private int id;
    private String subscriberName;

    public int getId() {

        return id;
    }

    public void setId(int id) {

        this.id = id;
    }

    public int getTenantId() {

        return tenantId;
    }

    public void setTenantId(int tenantId) {

        this.tenantId = tenantId;
    }

    public String getSubscriberName() {

        return subscriberName;
    }

    public void setSubscriberName(String subscriberName) {

        this.subscriberName = subscriberName;
    }

    public String getCustomerId() {

        return customerId;
    }

    public void setCustomerId(String customerId) {

        this.customerId = customerId;
    }
}
