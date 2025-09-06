package org.wso2.apim.monetization.impl.model.billing;

/**
 * Represents a generic customer in the billing system
 */
public class Customer {

    private String id;
    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
