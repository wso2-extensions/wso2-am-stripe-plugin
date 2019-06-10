package org.wso2.apim.monetization.impl.model;

public class MonetizedSubscription {

    private int id;
    private int subscribedApiId;
    private int subscribedApplicationId;
    private int tenantId;
    private String subscriptionId;
    private int sharedCustomerId;

    public int getId() {

        return id;
    }

    public void setId(int id) {

        this.id = id;
    }

    public int getSubscribedApiId() {

        return subscribedApiId;
    }

    public void setSubscribedApiId(int subscribedApiId) {

        this.subscribedApiId = subscribedApiId;
    }

    public int getSubscribedApplicationId() {

        return subscribedApplicationId;
    }

    public void setSubscribedApplicationId(int subscribedApplicationId) {

        this.subscribedApplicationId = subscribedApplicationId;
    }

    public int getTenantId() {

        return tenantId;
    }

    public void setTenantId(int tenantId) {

        this.tenantId = tenantId;
    }

    public String getSubscriptionId() {

        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {

        this.subscriptionId = subscriptionId;
    }

    public int getSharedCustomerId() {

        return sharedCustomerId;
    }

    public void setSharedCustomerId(int sharedCustomerId) {

        this.sharedCustomerId = sharedCustomerId;
    }

}
