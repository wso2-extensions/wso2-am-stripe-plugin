package org.wso2.apim.monetization.impl.constants;

public class StripeMonetizationConstants {
    public static final String MONETIZATION_INFO = "MonetizationInfo";
    public static final String BILLING_ENGINE_PLATFORM_ACCOUNT_KEY = "BillingEnginePlatformAccountKey";

    public static final String ADD_MONETIZATION_DATA_SQL = "INSERT INTO AM_MONETIZATION_MOESIF VALUES (?,?,?,?,?)";

    public static final String GET_PRICE_ID_FOR_API_AND_TIER = "SELECT MOESIF_PRICE_ID FROM AM_MONETIZATION_MOESIF " +
            "WHERE API_ID = ? AND TIER_NAME = ?";


}
