package org.wso2.apim.monetization.impl.model;

public class MoesifPlanInfo {
    private String planId;
    private String priceId;
    private String planName;

    public MoesifPlanInfo(String planId, String priceId, String planName) {
        this.planId = planId;
        this.priceId = priceId;
        this.planName = planName;
    }

    public String getPlanId() {
        return planId;
    }

    public String getPriceId() {
        return priceId;
    }

    public String getPlanName() {
        return planName;
    }
}
