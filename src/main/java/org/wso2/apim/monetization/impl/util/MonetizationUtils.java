/*
 *  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apim.monetization.impl.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.MoesifMonetizationException;
import org.wso2.apim.monetization.impl.constants.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.constants.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.enums.MoesifPricingModel;
import org.wso2.apim.monetization.impl.enums.Provider;
import org.wso2.apim.monetization.impl.model.MoesifPlanInfo;
import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MonetizationUtils {

    private static final Log log = LogFactory.getLog(MonetizationUtils.class);


    /**
     * Creates a billing price in Moesif for a given plan and tier.
     *
     * @param currentTier          The monetization tier details.
     * @param planId               The Moesif plan ID under which this price should be created.
     * @param moesifApplicationKey The Moesif application key for authentication.
     * @param moesifPlanName       The associated Moesif plan name (used for meter naming).
     * @return The response from Moesif API after creating the billing price.
     * @throws MoesifMonetizationException if any error occurs while creating the price.
     */
    public static String createPriceInMoesif(Tier currentTier, String planId,
                                             String moesifApplicationKey, String moesifPlanName)
            throws MoesifMonetizationException {
        String createPriceUrl;
        String priceResponse;

        try {
            // Construct billing price URL for Stripe
            createPriceUrl = MonetizationUtils.constructProviderURL(
                    MoesifMonetizationConstants.BILLING_PRICE_URL, Provider.STRIPE);

            // Build price creation payload
            JsonObject createPricePayload = new JsonObject();
            createPricePayload.addProperty("name", currentTier.getName());
            createPricePayload.addProperty("provider", Provider.STRIPE.getValue());
            createPricePayload.addProperty("plan_id", planId);
            createPricePayload.addProperty("status", MoesifMonetizationConstants.BILLING_PRICE_STATUS_ACTIVE);

            // Decide pricing model based on tier attributes
            String pricePerRequest = currentTier.getMonetizationAttributes().get("pricePerRequest");
            String fixedPrice = currentTier.getMonetizationAttributes().get("fixedPrice");

            if (pricePerRequest != null && !pricePerRequest.isEmpty()) {
                // Per-unit (metered) pricing model
                createPricePayload.addProperty("pricing_model", MoesifPricingModel.PER_UNIT.getValue());
                createPricePayload.addProperty("price_in_decimal", pricePerRequest);

                // Attach billing meter
                JsonObject billingMeterPayload = new JsonObject();
                billingMeterPayload.addProperty("display_name", currentTier.getName() + " Meter");
                billingMeterPayload.addProperty("event_name", moesifPlanName + " Event");
                createPricePayload.add("price_meter", billingMeterPayload);

                //Todo: The flow for the fixed price tier should be implemented and tested
            } else if (fixedPrice != null && !fixedPrice.isEmpty()) {
                // Flat-rate pricing model
                createPricePayload.addProperty("pricing_model", MoesifPricingModel.FLAT_RATE.getValue());
                createPricePayload.addProperty("price_in_decimal", fixedPrice);

                // Placeholder for governance rule attachment
                createPricePayload.addProperty("usage_aggregator", "");
            }

            //Todo: period and period_units should be handled dynamically,
            // the possible set of values are not available in the Moesif openAPI spec
            // once identified the 'billingCycle' can be used and reformat to match the expected format of Moesif
            createPricePayload.addProperty("period", 1);
            createPricePayload.addProperty("period_units", "M");
            createPricePayload.addProperty("currency",
                    currentTier.getMonetizationAttributes().get("currencyType"));

            priceResponse = MonetizationUtils.invokeService("POST",
                    createPriceUrl, createPricePayload.toString(), moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Price creation payload: " + createPricePayload);
                log.debug("Price creation response: " + priceResponse);
            }
            log.info("Moesif billing price created successfully for tier: " + currentTier.getName());

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif billing price for tier [%s] under plan [%s]",
                    currentTier.getName(), planId);
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }

        return priceResponse;
    }


    /**
     * Creates a billing plan in Moesif for the given API.
     *
     * @param api The API object for which the billing plan should be created.
     * @return The response from Moesif API after creating the billing plan.
     * @throws MoesifMonetizationException if any error occurs while creating the plan.
     */
    public static String createPlanInMoesif(API api, String moesifApplicationKey, String moesifPlanName)
            throws MoesifMonetizationException {

        String createBillingPlanURL;
        String planResponse;

        try {

            // Construct billing plan URL for provider (Stripe in this case)
            createBillingPlanURL = MonetizationUtils.constructProviderURL(
                    MoesifMonetizationConstants.BILLING_PLANS_URL, Provider.STRIPE);

            // Prepare JSON payload
            JsonObject createPlanPayload = new JsonObject();
            createPlanPayload.addProperty("name", moesifPlanName);
            createPlanPayload.addProperty("status", MoesifMonetizationConstants.BILLING_PLAN_STATUS_ACTIVE);
            createPlanPayload.addProperty("provider", Provider.STRIPE.getValue());

            planResponse = MonetizationUtils.invokeService("POST", createBillingPlanURL, createPlanPayload.toString(),
                    moesifApplicationKey);

            if (log.isDebugEnabled()) {
                log.debug("Plan creation payload: " + createPlanPayload);
            }
            log.info("Plan created successfully in Moesif: " + moesifPlanName);

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Error while creating Moesif billing plan for API [name: %s, version: %s, provider: %s]",
                    api.getId().getApiName(), api.getId().getVersion(), api.getId().getProviderName());
            log.error(errorMessage, e);
            throw new MoesifMonetizationException(errorMessage, e);
        }

        return planResponse;
    }


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

            String response = MonetizationUtils.invokeService("POST",
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

        // TODO: Dynamically generate slug, url_query, and es_query based on the use case.
        //  Ensure that the valid range of values is clearly defined and validated beforehand.
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

        MonetizationUtils.invokeService("POST", MoesifMonetizationConstants.BILLING_METER_URL,
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
     * Invokes an HTTP service with the specified method, URL, payload, and authorization token.
     *
     * @param method  The HTTP method to use (e.g., "GET", "POST", "PUT", "DELETE").
     * @param url     The URL of the service to invoke.
     * @param payload The request payload for methods like POST and PUT (can be null for GET and DELETE).
     * @param token   The authorization token to include in the request headers (can be null if not needed).
     * @return The response body as a String.
     * @throws IOException            if an I/O exception occurs.
     * @throws APIManagementException if an error response is returned from the service.
     */
    public static String invokeService(String method, String url, String payload, String token)
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
     * @param URL URL
     * @param provider provider
     * @return String formatted URL
     */
    public static String constructProviderURL(String URL, Provider provider) {
        return String.format(URL, provider.getValue());
    }

    public static String getBillingReport(String subscriptionId, String token)
            throws IOException, APIManagementException, URISyntaxException {

        URI billingReportURL = new URIBuilder(MoesifMonetizationConstants.BILLING_REPORT_URL)
                .addParameter("subscription_id", subscriptionId)
                .build();
        return invokeService("GET", billingReportURL.toString(), null, token);
    }

    /**
     * Extracts the "id" field from a JSON response string.
     *
     * @param jsonResponse The JSON response string from which to extract the ID.
     * @return The extracted ID as a String, or null if not found.
     */
    public static String extractId(String jsonResponse) {
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        if (jsonObject.has("id") && !jsonObject.get("id").isJsonNull()) {
            return jsonObject.get("id").getAsString();
        }
        return null;
    }
}
