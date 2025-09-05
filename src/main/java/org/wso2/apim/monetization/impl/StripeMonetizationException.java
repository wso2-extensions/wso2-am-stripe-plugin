package org.wso2.apim.monetization.impl;

import org.wso2.carbon.apimgt.api.MonetizationException;

public class StripeMonetizationException extends MonetizationException {

    public StripeMonetizationException(String msg) {
        super(msg);
    }

    public StripeMonetizationException(String msg, Throwable e) {
        super(msg, e);
    }

    public StripeMonetizationException(Throwable throwable) {
        super(throwable);
    }

}
