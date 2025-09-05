package org.wso2.apim.monetization.impl;

import org.wso2.carbon.apimgt.api.MonetizationException;

public class MoesifMonetizationException extends MonetizationException {
    public MoesifMonetizationException(String msg) {
        super(msg);
    }

    public MoesifMonetizationException(String msg, Throwable e) {
        super(msg, e);
    }

    public MoesifMonetizationException(Throwable throwable) {
        super(throwable);
    }
}
