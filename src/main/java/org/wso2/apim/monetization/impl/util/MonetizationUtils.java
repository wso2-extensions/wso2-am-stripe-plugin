package org.wso2.apim.monetization.impl.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.MoesifMonetizationException;
import org.wso2.apim.monetization.impl.constants.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.constants.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.enums.Provider;
import org.wso2.apim.monetization.impl.model.MoesifPlanInfo;
import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MonetizationUtils {

    private static final Log log = LogFactory.getLog(MonetizationUtils.class);


    /**
     * Creates a user in Moesif based on the given Stripe customer.
     *
     * @param customer             The Stripe customer object.
     * @param moesifApplicationKey The Moesif application key for authentication.
     * @throws MoesifMonetizationException if user creation fails.
     */
    public static void createUserInMoesif(Customer customer, String moesifApplicationKey)
            throws MoesifMonetizationException {

        try {
            // Build user creation payload
            JsonObject createUserPayload = new JsonObject();
            createUserPayload.addProperty("user_id", customer.getName());
            createUserPayload.addProperty("company_id", customer.getId());
            createUserPayload.addProperty("name", customer.getName());

            String response = MonetizationUtils.invokeService(
                    MoesifMonetizationConstants.MOESIF_USER_URL, createUserPayload.toString(), moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Moesif user creation payload: " + createUserPayload);
                log.debug("Moesif user creation response: " + response);
            }
            log.info("Moesif user created successfully for customer: " + customer.getName());

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif user for Stripe customer [id: %s, name: %s]",
                    customer.getId(), customer.getName());
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }
    }

    /**
     * Creates a billing meter in Moesif for the given subscription and plan info.
     *
     * @param subscriptionId The ID of the subscription.
     * @param moesifPlanInfo The Moesif plan information.
     * @param key            The Moesif application key for authentication.
     * @throws IOException            if an I/O exception occurs.
     * @throws APIManagementException if an error response is returned from the service.
     */
    public static void createBillingMeterInMoesif(String subscriptionId, MoesifPlanInfo moesifPlanInfo, String key)
            throws IOException, APIManagementException {
        JsonObject createBillingMeterPayload = new JsonObject();

        //ToDo: slug, url_query, and es_query should be dynamically generated based on the use case
        // but the valid range of values should be identified first
        createBillingMeterPayload.addProperty("name", subscriptionId + "-billing-meter");
        createBillingMeterPayload.addProperty("slug", "hourly_usage");
        createBillingMeterPayload.addProperty("status", MoesifMonetizationConstants.BILLING_METER_STATUS_ACTIVE);

        createBillingMeterPayload.addProperty(
                "url_query",
                "?seg[metrics][0][metricName]=sum%28events%29&seg[groups][0][field]=company_id.raw&seg[groups][0][func]=terms&seg[groups][0][buckets]=25&seg[chartType]=table"
        );

        JsonObject payload = new JsonObject();

        // ===== query =====
        JsonObject query = new JsonObject();
        JsonObject bool = new JsonObject();
        JsonArray mustArray = new JsonArray();

        // --- first must -> bool should ---
        JsonObject must1 = new JsonObject();
        JsonObject innerBool = new JsonObject();
        JsonArray shouldArray = new JsonArray();

        // should -> terms subscription_id.raw
        JsonObject should1 = new JsonObject();
        JsonObject terms1 = new JsonObject();
        JsonArray subIds = new JsonArray();
        subIds.add("{{subscription_id}}");
        terms1.add("subscription_id.raw", subIds);
        should1.add("terms", terms1);
        shouldArray.add(should1);

        // should -> bool must_not exists subscription_id.raw
        JsonObject should2 = new JsonObject();
        JsonObject bool2 = new JsonObject();
        JsonObject mustNot = new JsonObject();
        JsonObject exists = new JsonObject();
        exists.addProperty("field", "subscription_id.raw");
        mustNot.add("exists", exists);
        bool2.add("must_not", mustNot);
        should2.add("bool", bool2);
        shouldArray.add(should2);

        // put should[] inside bool
        innerBool.add("should", shouldArray);
        must1.add("bool", innerBool);
        mustArray.add(must1);

        // --- second must -> terms company_id.raw ---
        JsonObject must2 = new JsonObject();
        JsonObject terms2 = new JsonObject();
        JsonArray companyIds = new JsonArray();
        companyIds.add("{{company_id}}");
        terms2.add("company_id.raw", companyIds);
        must2.add("terms", terms2);
        mustArray.add(must2);

        // build bool.must
        bool.add("must", mustArray);
        query.add("bool", bool);
        payload.add("query", query);

        // ===== aggs =====
        JsonObject aggs = new JsonObject();
        JsonObject seg = new JsonObject();

        // filter: match_all
        JsonObject filter = new JsonObject();
        filter.add("match_all", new JsonObject());
        seg.add("filter", filter);

        // aggs inside seg
        JsonObject segAggs = new JsonObject();
        JsonObject usageValue = new JsonObject();
        JsonObject sum = new JsonObject();
        sum.addProperty("field", "weight");
        sum.addProperty("missing", 1);
        usageValue.add("sum", sum);
        segAggs.add("usage_value", usageValue);
        seg.add("aggs", segAggs);

        // add seg under aggs
        aggs.add("seg", seg);
        payload.add("aggs", aggs);

        // ===== size =====
        payload.addProperty("size", 0);

        // ===== final =====
        System.out.println(payload.toString());

        // attach to your createBillingMeterPayload
        createBillingMeterPayload.add("es_query", payload);


        // billing_plan
        JsonObject billingPlan = new JsonObject();
        billingPlan.addProperty("provider_slug", Provider.STRIPE.getValue());

        JsonObject params = new JsonObject();

        // stripe_params
        JsonObject stripeParams = new JsonObject();

        // product
        JsonObject product = new JsonObject();
        product.addProperty("name", moesifPlanInfo.getPlanName());
        product.addProperty("id", moesifPlanInfo.getPlanId());
        stripeParams.add("product", product);

        // prices
        JsonArray pricesArray = new JsonArray();
        JsonObject price = new JsonObject();
        price.addProperty("price_id", moesifPlanInfo.getPriceId());
        pricesArray.add(price);
        stripeParams.add("prices", pricesArray);

        // reporting
        JsonObject reporting = new JsonObject();
        reporting.addProperty("reporting_period", "5m");
        stripeParams.add("reporting", reporting);

        // add stripe_params
        params.add("stripe_params", stripeParams);
        params.addProperty("usage_multiplier", 1);
        params.addProperty("usage_rounding_mode", "up");

        // add params to billing_plan
        billingPlan.add("params", params);

        // add billing_plan to root
        createBillingMeterPayload.add("billing_plan", billingPlan);

        if (log.isDebugEnabled()) {
            log.debug("Moesif billing meter creation payload: " + createBillingMeterPayload);
        }

        MonetizationUtils.invokeService(MoesifMonetizationConstants.BILLING_METER_URL,
                createBillingMeterPayload.toString(), key);

    }

    /**
     * Get the platform account key of the tenant
     *
     * @param tenantDomain tenant domain of the user
     * @return platform account key
     * @throws WorkflowException if failed to get the platform account key
     */
    public static String getPlatformAccountKey(String tenantDomain) throws WorkflowException {

        String stripePlatformAccountKey = null;
        try {
            //get the stripe key of platform account from  tenant conf json file
            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);
            if (tenantConfig.containsKey(StripeMonetizationConstants.MONETIZATION_INFO)) {
                JSONObject monetizationInfo = (JSONObject) tenantConfig
                        .get(StripeMonetizationConstants.MONETIZATION_INFO);
                if (monetizationInfo.containsKey(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY)) {
                    stripePlatformAccountKey = monetizationInfo
                            .get(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY).toString();
                    if (StringUtils.isBlank(stripePlatformAccountKey)) {
                        String errorMessage = "Stripe platform account key is empty for tenant : " + tenantDomain;
                        throw new WorkflowException(errorMessage);
                    }
                    return stripePlatformAccountKey;
                }
            }
        } catch (APIManagementException e) {
            throw new WorkflowException("Failed to get the configuration for tenant from DB:  " + tenantDomain, e);
        }

        return stripePlatformAccountKey;
    }

    /**
     * Get the Moesif application key of the tenant
     *
     * @param tenantDomain tenant domain of the user
     * @return Moesif application key
     * @throws MoesifMonetizationException if failed to get the Moesif application key
     */
    public static String getMoesifApplicationKey(String tenantDomain) throws MoesifMonetizationException {

        try {
            //get the application key of platform account from tenant conf json file
            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);

            if (tenantConfig.containsKey(MoesifMonetizationConstants.MONETIZATION_INFO)) {
                JSONObject monetizationInfo = (JSONObject) tenantConfig
                        .get(MoesifMonetizationConstants.MONETIZATION_INFO);
                if (monetizationInfo.containsKey(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY)) {
                    String moesifApplicationKey = monetizationInfo
                            .get(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY).toString();
                    if (StringUtils.isBlank(moesifApplicationKey)) {
                        String errorMessage = "Moesif application key is empty for tenant : " + tenantDomain;
                        throw new MoesifMonetizationException(errorMessage);
                    }
                    return moesifApplicationKey;
                }
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get the configuration for tenant from DB:  " + tenantDomain;
            log.error(errorMessage);
            throw new MoesifMonetizationException(errorMessage, e);
        }
        return StringUtils.EMPTY;
    }

    /**
     * Invoke a REST API service
     *
     * @param url     URL of the service
     * @param payload Payload to be sent to the service
     * @param token   Bearer token if any
     * @return Response of the service
     * @throws IOException            if an I/O exception occurs
     * @throws APIManagementException if an error response is returned from the service
     */
    public static String invokeService(String url, String payload, String token) throws IOException, APIManagementException {
        HttpClient httpClient = APIUtil.getHttpClient(url); // keep pooled client

        HttpPost post = new HttpPost(url);
        post.setHeader(APIConstants.HEADER_CONTENT_TYPE, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        post.setHeader(APIConstants.HEADER_ACCEPT, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        if (token != null && !token.isEmpty()) {
            post.setHeader("Authorization", "Bearer " + token);
        }

        if (payload != null) {
            post.setEntity(new StringEntity(payload, "UTF-8"));
        }

        try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new APIManagementException("Moesif call failed [" + statusCode + "] " + responseBody);
            }
        }
    }

    public static String invokeService(String url, String method, String payload, String token)
            throws IOException, APIManagementException {

        HttpClient httpClient = APIUtil.getHttpClient(url); // pooled client
        HttpUriRequest request;

        switch (method.toUpperCase()) {
            case "POST":
                HttpPost post = new HttpPost(url);
                if (payload != null) {
                    post.setEntity(new StringEntity(payload, "UTF-8"));
                }
                request = post;
                break;

            case "PUT":
                HttpPut put = new HttpPut(url);
                if (payload != null) {
                    put.setEntity(new StringEntity(payload, "UTF-8"));
                }
                request = put;
                break;

            case "DELETE":
                request = new HttpDelete(url);
                break;

            case "GET":
            default:
                request = new HttpGet(url);
                break;
        }

        // Common headers
        request.setHeader(APIConstants.HEADER_CONTENT_TYPE, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        request.setHeader(APIConstants.HEADER_ACCEPT, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        if (token != null && !token.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + token);
        }

        try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new APIManagementException("Moesif call failed [" + statusCode + "] " + responseBody);
            }
        }
    }


    /***
     * Construct the URL according to the provider
     *
     *
     * @param URL
     * @param provider
     * @return String formatted URL
     */
    public static String constructProviderURL(String URL, Provider provider) {
        return String.format(URL, provider.getValue());
    }

    public static String getBillingReport(String subscriptionId, String token)
            throws IOException, APIManagementException {

        String url = MoesifMonetizationConstants.BILLING_REPORT_URL + "?subscription_id=" +
                URLEncoder.encode(subscriptionId, StandardCharsets.UTF_8);
        return invokeService(url, "GET", null, token);
    }
}
