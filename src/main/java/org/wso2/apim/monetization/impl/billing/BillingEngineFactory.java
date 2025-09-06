package org.wso2.apim.monetization.impl.billing;

public class BillingEngineFactory {

    public static BillingEngine getBillingEngine() {
        // For now, we only support Stripe
        // In the future, we can use a configuration to determine which billing engine to use
        return new StripeBillingEngine();
    }
}
