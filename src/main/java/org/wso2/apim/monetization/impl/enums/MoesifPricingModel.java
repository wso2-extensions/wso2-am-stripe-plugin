package org.wso2.apim.monetization.impl.enums;

public enum MoesifPricingModel {

    FLAT_RATE("flat"),
    PER_UNIT("per_unit");
    private final String value;

    MoesifPricingModel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
