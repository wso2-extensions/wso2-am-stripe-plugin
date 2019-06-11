package org.wso2.apim.monetization.impl;

/**
 * This class is to define constants related to stripe based monetization
 */
public class StripeMonetizationConstants {

    /**
     * Stripe based monetization related constants
     **/
    public static final String ADD_MONETIZATION_DATA_SQL = "INSERT INTO AM_MONETIZATION VALUES (?,?,?,?)";
    public static final String DELETE_MONETIZATION_DATA_SQL = "DELETE FROM AM_MONETIZATION WHERE API_ID = ?";
    public static final String GET_BILLING_ENGINE_PRODUCT_BY_API = "SELECT STRIPE_PRODUCT_ID FROM AM_MONETIZATION WHERE API_ID = ? ";
    public static final String GET_BILLING_ENGINE_SUBSCRIPTION_ID = "SELECT SUBSCRIPTION_ID FROM " +
            "AM_MONETIZATION_SUBSCRIPTIONS " +
            "WHERE SUBSCRIBED_APPLICATION_ID = ? AND SUBSCRIBED_API_ID = ?";
    public static final String GET_BILLING_PLANS_BY_PRODUCT = "SELECT TIER_NAME, STRIPE_PLAN_ID FROM AM_MONETIZATION " +
            "WHERE API_ID = ? AND STRIPE_PRODUCT_ID = ?";
    public static final String GET_BILLING_PLAN_FOR_TIER = "SELECT STRIPE_PLAN_ID FROM AM_MONETIZATION " +
            "WHERE API_ID = ? AND TIER_NAME = ?";
    public static final String INSERT_MONETIZATION_PLAN_DATA_SQL =
            "INSERT INTO AM_POLICY_PLAN_MAPPING (POLICY_UUID, PRODUCT_ID, PLAN_ID) VALUES (?,?,?)";
    public static final String UPDATE_MONETIZATION_PLAN_ID_SQL = "UPDATE AM_POLICY_PLAN_MAPPING SET PLAN_ID = ? " +
            "WHERE POLICY_UUID = ? AND PRODUCT_ID = ?";
    public static final String DELETE_MONETIZATION_PLAN_DATA = "DELETE FROM AM_POLICY_PLAN_MAPPING WHERE " +
            "POLICY_UUID = ?";
    public static final String GET_BILLING_PLAN_DATA = "SELECT PRODUCT_ID, PLAN_ID FROM AM_POLICY_PLAN_MAPPING " +
            "WHERE POLICY_UUID = ?";
    public static final String GET_BILLING_PLAN_ID = "SELECT PLAN_ID FROM AM_POLICY_PLAN_MAPPING " +
            "WHERE POLICY_UUID = ?";
    public static final String GET_SUBSCRIPTION_UUID = "SELECT UUID FROM AM_SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";

    public static final String TYPE = "type";
    public static final String SERVICE_TYPE = "service";
    public static final String CURRENCY = "currency";
    public static final String BILLING_SCHEME = "billing_scheme";
    public static final String USD = "usd";
    public static final String PRODUCT = "product";
    public static final String PRODUCT_NICKNAME = "nickname";
    public static final String INTERVAL = "interval";
    public static final String BILLING_CYCLE = "billingCycle";
    public static final String FIXED_RATE = "fixedRate";
    public static final String DYNAMIC_RATE = "dynamicRate";
    public static final String FIXED_PRICE = "fixedPrice";
    public static final String AMOUNT = "amount";
    public static final String USAGE_TYPE = "usage_type";
    public static final String LICENSED_USAGE = "licensed";
    public static final String METERED_USAGE = "metered";
    public static final String PRICE_PER_REQUEST = "pricePerRequest";
    public static final String PRODUCT_ID = "productId";
    public static final String PLAN_ID = "planId";
    public static final String API_MONETIZATION_STATUS = "isMonetizationEnabled";
    public static final String API_MONETIZATION_PROPERTIES = "monetizationProperties";
    public static final String MONETIZATION_INFO = "MonetizationInfo";
    public static final String BILLING_ENGINE_PLATFORM_ACCOUNT_KEY = "BillingEnginePlatformAccountKey";
    public static final String BILLING_ENGINE_CONNECTED_ACCOUNT_KEY = "ConnectedAccountKey";

}
