package org.wso2.apim.monetization.impl.enums;

public enum Provider {
    STRIPE("stripe"),
    CHARGEBEE("chargebee"),
    RECURLY("recurly"),
    ZUORA("zuora");

    private final String value;

    Provider(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
}
