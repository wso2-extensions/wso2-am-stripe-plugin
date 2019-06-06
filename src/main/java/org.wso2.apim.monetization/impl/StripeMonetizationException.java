package org.wso2.apim.monetization.impl;

/**
 * This is the custom exception class for API monetization.
 */
public class StripeMonetizationException extends Exception {

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
