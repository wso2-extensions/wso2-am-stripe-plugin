package org.wso2.apim.monetization.impl.model;

public class MonetizationSharedCustomer {

    private int id;
    private int applicationId;

    public String getApiProvider() {

        return apiProvider;
    }

    public void setApiProvider(String apiProvider) {

        this.apiProvider = apiProvider;
    }

    private String apiProvider;
    private int tenantId;

    public int getId() {

        return id;
    }

    public void setId(int id) {

        this.id = id;
    }

    public int getApplicationId() {

        return applicationId;
    }

    public void setApplicationId(int applicationId) {

        this.applicationId = applicationId;
    }

    public int getTenantId() {

        return tenantId;
    }

    public void setTenantId(int tenantId) {

        this.tenantId = tenantId;
    }

    public String getSharedCustomerId() {

        return sharedCustomerId;
    }

    public void setSharedCustomerId(String sharedCustomerId) {

        this.sharedCustomerId = sharedCustomerId;
    }

    public int getParentCustomerId() {

        return parentCustomerId;
    }

    public void setParentCustomerId(int parentCustomerId) {

        this.parentCustomerId = parentCustomerId;
    }

    private String sharedCustomerId;
    private int parentCustomerId;

}
