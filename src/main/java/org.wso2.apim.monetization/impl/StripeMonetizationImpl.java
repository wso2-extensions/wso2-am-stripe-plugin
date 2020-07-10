/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.apim.monetization.impl;

import com.google.gson.Gson;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.Plan;
import com.stripe.model.Product;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.UsageRecord;
import com.stripe.net.RequestOptions;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.apim.monetization.impl.model.MonetizedSubscription;
import org.wso2.carbon.apimgt.api.APIAdmin;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIProduct;
import org.wso2.carbon.apimgt.api.model.APIProductIdentifier;
import org.wso2.carbon.apimgt.api.model.Monetization;
import org.wso2.carbon.apimgt.api.model.MonetizationUsagePublishInfo;
import org.wso2.carbon.apimgt.api.model.SubscribedAPI;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.APIAdminImpl;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * This class is used to implement stripe based monetization
 */
public class StripeMonetizationImpl implements Monetization {

    private static final Log log = LogFactory.getLog(StripeMonetizationImpl.class);
    private StripeMonetizationDAO stripeMonetizationDAO = StripeMonetizationDAO.getInstance();

    /**
     * Create billing plan for a policy
     *
     * @param subscriptionPolicy subscription policy
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean createBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {

        try {
            //read tenant conf and get platform account key
            Stripe.apiKey = getStripePlatformAccountKey(subscriptionPolicy.getTenantDomain());
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                    subscriptionPolicy.getTenantDomain();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        Map<String, Object> productParams = new HashMap<String, Object>();
        productParams.put(APIConstants.POLICY_NAME_ELEM, subscriptionPolicy.getTenantDomain() +
                "-" + subscriptionPolicy.getPolicyName());
        productParams.put(APIConstants.TYPE, StripeMonetizationConstants.SERVICE_TYPE);
        Timestamp timestamp = new Timestamp(new Date().getTime());
        String productCreationIdempotencyKey = subscriptionPolicy.getTenantDomain() + timestamp.toString();
        RequestOptions productRequestOptions = RequestOptions.builder().
                setIdempotencyKey(productCreationIdempotencyKey).build();
        try {
            Product product = Product.create(productParams, productRequestOptions);
            String productId = product.getId();
            if (StringUtils.isBlank(productId)) {
                String errorMessage = "Failed to create stripe product for tenant : " +
                        subscriptionPolicy.getTenantDomain();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
            Map<String, Object> planParams = new HashMap<String, Object>();
            String currencyType = subscriptionPolicy.getMonetizationPlanProperties().
                    get(APIConstants.Monetization.CURRENCY).toLowerCase();
            planParams.put(StripeMonetizationConstants.CURRENCY, currencyType);
            planParams.put(StripeMonetizationConstants.PRODUCT, productId);
            planParams.put(StripeMonetizationConstants.PRODUCT_NICKNAME, subscriptionPolicy.getPolicyName());
            planParams.put(StripeMonetizationConstants.INTERVAL,
                    subscriptionPolicy.getMonetizationPlanProperties().get(APIConstants.Monetization.BILLING_CYCLE));
            if (APIConstants.Monetization.FIXED_RATE.equalsIgnoreCase(subscriptionPolicy.getMonetizationPlan())) {
                float amount = Float.parseFloat(subscriptionPolicy.getMonetizationPlanProperties().
                        get(APIConstants.Monetization.FIXED_PRICE));
                //need to multiply the input for "amount" by 100 for stripe (because it divides the value by 100)
                //also, since stripe supports only integers, convert the amount to an int before creating the plan
                planParams.put(StripeMonetizationConstants.AMOUNT, (int) (amount * 100));
                planParams.put(StripeMonetizationConstants.USAGE_TYPE, StripeMonetizationConstants.LICENSED_USAGE);
            }
            if (StripeMonetizationConstants.DYNAMIC_RATE.equalsIgnoreCase(subscriptionPolicy.getMonetizationPlan())) {
                float amount = Float.parseFloat(subscriptionPolicy.getMonetizationPlanProperties().
                        get(APIConstants.Monetization.PRICE_PER_REQUEST));
                //need to multiply the input for "amount" by 100 for stripe (because it divides the value by 100)
                //also, since stripe supports only integers, convert the amount to an int before creating the plan
                planParams.put(StripeMonetizationConstants.AMOUNT, (int) (amount * 100));
                planParams.put(StripeMonetizationConstants.USAGE_TYPE, StripeMonetizationConstants.METERED_USAGE);
            }
            RequestOptions planRequestOptions = RequestOptions.builder().
                    setIdempotencyKey(subscriptionPolicy.getUUID()).build();
            Plan plan = Plan.create(planParams, planRequestOptions);
            String createdPlanId = plan.getId();
            //put the newly created stripe plans and tiers into a map (to add data to the database)
            if (StringUtils.isBlank(createdPlanId)) {
                String errorMessage = "Failed to create plan for tier : " + subscriptionPolicy.getPolicyName() +
                        " in " + subscriptionPolicy.getTenantDomain();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
            //add database record
            stripeMonetizationDAO.addMonetizationPlanData(subscriptionPolicy, productId, createdPlanId);
            return true;
        } catch (StripeException e) {
            String errorMessage = "Failed to create monetization plan for : " + subscriptionPolicy.getPolicyName() +
                    " in stripe.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to create monetization plan for : " + subscriptionPolicy.getPolicyName() +
                    " in the database.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
    }

    /**
     * Update billing plan of a policy
     *
     * @param subscriptionPolicy subscription policy
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean updateBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {

        Map<String, String> planData = null;
        try {
            planData = stripeMonetizationDAO.getPlanData(subscriptionPolicy);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get stripe plan data for policy : " + subscriptionPolicy.getPolicyName() +
                    " when updating billing plan.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        String oldProductId = null;
        String oldPlanId = null;
        String newProductId = null;
        String updatedPlanId = null;
        try {
            //read tenant-conf.json and get platform account key
            Stripe.apiKey = getStripePlatformAccountKey(subscriptionPolicy.getTenantDomain());
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                    subscriptionPolicy.getTenantDomain() + " when updating billing plan.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        if (MapUtils.isNotEmpty(planData)) {
            //product and plan exists for the older plan, so get those values and proceed
            oldProductId = planData.get(StripeMonetizationConstants.PRODUCT_ID);
            oldPlanId = planData.get(StripeMonetizationConstants.PLAN_ID);
        } else {
            //this means updating the monetization plan of tier from a free to commercial.
            //since there is no plan (for old - free tier), we should create a product and plan for the updated tier
            Map<String, Object> productParams = new HashMap<String, Object>();
            productParams.put(APIConstants.POLICY_NAME_ELEM,
                    subscriptionPolicy.getTenantDomain() + "-" + subscriptionPolicy.getPolicyName());
            productParams.put(APIConstants.TYPE, StripeMonetizationConstants.SERVICE_TYPE);
            Timestamp timestamp = new Timestamp(new Date().getTime());
            String productCreationIdempotencyKey = subscriptionPolicy.getTenantDomain() + timestamp.toString();
            RequestOptions productRequestOptions = RequestOptions.builder().
                    setIdempotencyKey(productCreationIdempotencyKey).build();
            try {
                Product product = Product.create(productParams, productRequestOptions);
                newProductId = product.getId();
                if (StringUtils.isBlank(newProductId)) {
                    String errorMessage = "No stripe product was created for tenant (when updating policy) : " +
                            subscriptionPolicy.getTenantDomain();
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
            } catch (StripeException e) {
                String errorMessage = "Failed to create stripe product for tenant (when updating policy) : " +
                        subscriptionPolicy.getTenantDomain();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            }
        }
        //delete old plan if exists
        if (StringUtils.isNotBlank(oldPlanId)) {
            try {
                Plan.retrieve(oldPlanId).delete();
            } catch (StripeException e) {
                String errorMessage = "Failed to delete old plan for policy : " + subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            }
        }
        //if updated to a commercial plan, create new plan in billing engine and update DB record
        if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(subscriptionPolicy.getBillingPlan())) {
            Map<String, Object> planParams = new HashMap<String, Object>();
            String currencyType = subscriptionPolicy.getMonetizationPlanProperties().
                    get(APIConstants.Monetization.CURRENCY).toLowerCase();
            planParams.put(StripeMonetizationConstants.CURRENCY, currencyType);
            if (StringUtils.isNotBlank(oldProductId)) {
                planParams.put(StripeMonetizationConstants.PRODUCT, oldProductId);
            }
            if (StringUtils.isNotBlank(newProductId)) {
                planParams.put(StripeMonetizationConstants.PRODUCT, newProductId);
            }
            planParams.put(StripeMonetizationConstants.PRODUCT_NICKNAME, subscriptionPolicy.getPolicyName());
            planParams.put(StripeMonetizationConstants.INTERVAL, subscriptionPolicy.getMonetizationPlanProperties().
                    get(APIConstants.Monetization.BILLING_CYCLE));

            if (APIConstants.Monetization.FIXED_RATE.equalsIgnoreCase(subscriptionPolicy.getMonetizationPlan())) {
                float amount = Float.parseFloat(subscriptionPolicy.getMonetizationPlanProperties().
                        get(APIConstants.Monetization.FIXED_PRICE));
                //need to multiply the input for "amount" by 100 for stripe (because it divides the value by 100)
                //also, since stripe supports only integers, convert the amount to an int before creating the plan
                planParams.put(StripeMonetizationConstants.AMOUNT, (int) (amount * 100));
                planParams.put(StripeMonetizationConstants.USAGE_TYPE, StripeMonetizationConstants.LICENSED_USAGE);
            }
            if (StripeMonetizationConstants.DYNAMIC_RATE.equalsIgnoreCase(subscriptionPolicy.getMonetizationPlan())) {
                float amount = Float.parseFloat(subscriptionPolicy.getMonetizationPlanProperties().
                        get(APIConstants.Monetization.PRICE_PER_REQUEST));
                //need to multiply the input for "amount" by 100 for stripe (because it divides the value by 100)
                //also, since stripe supports only integers, convert the amount to an int before creating the plan
                planParams.put(StripeMonetizationConstants.AMOUNT, (int) (amount * 100));
                planParams.put(StripeMonetizationConstants.USAGE_TYPE, StripeMonetizationConstants.METERED_USAGE);
            }
            Plan updatedPlan = null;
            try {
                updatedPlan = Plan.create(planParams);
            } catch (StripeException e) {
                String errorMessage = "Failed to create stripe plan for tier : " + subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            }
            if (updatedPlan != null) {
                updatedPlanId = updatedPlan.getId();
            } else {
                String errorMessage = "Failed to create plan for policy update : " + subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
            if (StringUtils.isBlank(updatedPlanId)) {
                String errorMessage = "Failed to update stripe plan for tier : " + subscriptionPolicy.getPolicyName() +
                        " in " + subscriptionPolicy.getTenantDomain();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
        } else if (APIConstants.BILLING_PLAN_FREE.equalsIgnoreCase(subscriptionPolicy.getBillingPlan())) {
            try {
                //If updated to a free plan (from a commercial plan), no need to create any plan in the billing engine
                //hence delete the DB record
                stripeMonetizationDAO.deleteMonetizationPlanData(subscriptionPolicy);
                //Remove old artifacts in the billing engine (if any)
                if (StringUtils.isNotBlank(oldProductId)) {
                    Product.retrieve(oldProductId).delete();
                }
            } catch (StripeException e) {
                String errorMessage = "Failed to delete old stripe product for : " + subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            } catch (StripeMonetizationException e) {
                String errorMessage = "Failed to delete monetization plan data from database for : " +
                        subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            }
        }
        try {
            if (StringUtils.isNotBlank(oldProductId)) {
                //update DB record
                stripeMonetizationDAO.updateMonetizationPlanData(subscriptionPolicy, oldProductId, updatedPlanId);
            }
            if (StringUtils.isNotBlank(newProductId)) {
                //create new DB record
                stripeMonetizationDAO.addMonetizationPlanData(subscriptionPolicy, newProductId, updatedPlanId);
            }
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to update monetization plan data in database for : " +
                    subscriptionPolicy.getPolicyName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return true;
    }

    /**
     * Delete a billing plan of a policy
     *
     * @param subscriptionPolicy subscription policy
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean deleteBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {

        //get old plan (if any) in the billing engine and delete
        Map<String, String> planData = null;
        try {
            planData = stripeMonetizationDAO.getPlanData(subscriptionPolicy);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get stripe plan data for policy : " + subscriptionPolicy.getPolicyName() +
                    " when deleting billing plan.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        if (MapUtils.isEmpty(planData)) {
            log.debug("No billing plan found for : " + subscriptionPolicy.getPolicyName());
            return true;
        }
        String productId = planData.get(StripeMonetizationConstants.PRODUCT_ID);
        String planId = planData.get(StripeMonetizationConstants.PLAN_ID);
        try {
            //read tenant-conf.json and get platform account key
            Stripe.apiKey = getStripePlatformAccountKey(subscriptionPolicy.getTenantDomain());
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                    subscriptionPolicy.getTenantDomain() + " when deleting billing plan.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        if (StringUtils.isNotBlank(planId)) {
            try {
                Plan.retrieve(planId).delete();
                Product.retrieve(productId).delete();
                stripeMonetizationDAO.deleteMonetizationPlanData(subscriptionPolicy);
            } catch (StripeException e) {
                String errorMessage = "Failed to delete billing plan resources of : " + subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            } catch (StripeMonetizationException e) {
                String errorMessage = "Failed to delete billing plan data from database of policy : " +
                        subscriptionPolicy.getPolicyName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage, e);
            }
        }
        return true;
    }

    /**
     * Enable monetization for a API
     *
     * @param tenantDomain           tenant domain
     * @param api                    API
     * @param monetizationProperties monetization properties map
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean enableMonetization(String tenantDomain, API api, Map<String, String> monetizationProperties)
            throws MonetizationException {

        String platformAccountKey = null;
        try {
            //read tenant conf and get platform account key
            platformAccountKey = getStripePlatformAccountKey(tenantDomain);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                    tenantDomain + " when enabling monetization for : " + api.getId().getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        String connectedAccountKey;
        //get api publisher's stripe key (i.e - connected account key) from monetization properties in request payload
        if (MapUtils.isNotEmpty(monetizationProperties) &&
                monetizationProperties.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
            connectedAccountKey = monetizationProperties.get
                    (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
            if (StringUtils.isBlank(connectedAccountKey)) {
                String errorMessage = "Connected account stripe key was not found for : " + api.getId().getApiName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
        } else {
            String errorMessage = "Stripe key of the connected account is empty.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage);
        }
        String apiName = api.getId().getApiName();
        String apiVersion = api.getId().getVersion();
        String apiProvider = api.getId().getProviderName();
        try {
            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getId(), null);
            String billingProductIdForApi = getBillingProductIdForApi(apiId);
            //create billing engine product if it does not exist
            if (StringUtils.isEmpty(billingProductIdForApi)) {
                Stripe.apiKey = platformAccountKey;
                Map<String, Object> productParams = new HashMap<String, Object>();
                String stripeProductName = apiName + "-" + apiVersion + "-" + apiProvider;
                productParams.put(APIConstants.POLICY_NAME_ELEM, stripeProductName);
                productParams.put(APIConstants.TYPE, StripeMonetizationConstants.SERVICE_TYPE);
                RequestOptions productRequestOptions = RequestOptions.builder().setStripeAccount(
                        connectedAccountKey).build();
                try {
                    Product product = Product.create(productParams, productRequestOptions);
                    billingProductIdForApi = product.getId();
                } catch (StripeException e) {
                    String errorMessage = "Unable to create product in billing engine for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage, e);
                }
            }
            Map<String, String> tierPlanMap = new HashMap<String, String>();
            //scan for commercial tiers and add add plans in the billing engine if needed
            for (Tier currentTier : api.getAvailableTiers()) {
                if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(currentTier.getTierPlan())) {
                    String billingPlanId = getBillingPlanIdOfTier(apiId, currentTier.getName());
                    if (StringUtils.isBlank(billingPlanId)) {
                        int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                        String createdPlanId = createBillingPlanForCommercialTier(currentTier, tenantId,
                                platformAccountKey, connectedAccountKey, billingProductIdForApi);
                        if (StringUtils.isNotBlank(createdPlanId)) {
                            log.debug("Billing plan : " + createdPlanId + " successfully created for : " +
                                    currentTier.getName());
                            tierPlanMap.put(currentTier.getName(), createdPlanId);
                        } else {
                            log.debug("Failed to create billing plan for : " + currentTier.getName());
                        }
                    }
                }
            }
            //save data in the database - only if there is a stripe product and newly created plans
            if (StringUtils.isNotBlank(billingProductIdForApi) && MapUtils.isNotEmpty(tierPlanMap)) {
                stripeMonetizationDAO.addMonetizationData(apiId, billingProductIdForApi, tierPlanMap);
            } else {
                return false;
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get ID from database for : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to create products and plans in stripe for : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return true;
    }

    /**
     * Disable monetization for a API
     *
     * @param tenantDomain           tenant domain
     * @param api                    API
     * @param monetizationProperties monetization properties map
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean disableMonetization(String tenantDomain, API api, Map<String, String> monetizationProperties) throws MonetizationException {

        String platformAccountKey = null;
        try {
            //read tenant conf and get platform account key
            platformAccountKey = getStripePlatformAccountKey(tenantDomain);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                    tenantDomain + " when disabling monetization for : " + api.getId().getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        String connectedAccountKey = StringUtils.EMPTY;
        //get api publisher's stripe key (i.e - connected account key) from monetization properties in request payload
        if (MapUtils.isNotEmpty(monetizationProperties) &&
                monetizationProperties.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
            connectedAccountKey = monetizationProperties.get
                    (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
            if (StringUtils.isBlank(connectedAccountKey)) {
                String errorMessage = "Billing engine connected account key was not found for : " +
                        api.getId().getApiName();
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }
        } else {
            String errorMessage = "Stripe key of the connected account is empty for tenant : " + tenantDomain;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage);
        }
        try {
            String apiName = api.getId().getApiName();
            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getId(), null);
            String billingProductIdForApi = getBillingProductIdForApi(apiId);
            //no product in the billing engine, so return
            if (StringUtils.isBlank(billingProductIdForApi)) {
                return false;
            }
            Map<String, String> tierToBillingEnginePlanMap = stripeMonetizationDAO.getTierToBillingEnginePlanMapping
                    (apiId, billingProductIdForApi);
            Stripe.apiKey = platformAccountKey;
            RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();

            for (Map.Entry<String, String> entry : tierToBillingEnginePlanMap.entrySet()) {
                String planId = entry.getValue();
                Plan plan = Plan.retrieve(planId, requestOptions);
                plan.delete(requestOptions);
                log.debug("Successfully deleted billing plan : " + planId + " of tier : " + entry.getKey());
            }
            //after deleting all the associated plans, then delete the product
            Product product = Product.retrieve(billingProductIdForApi, requestOptions);
            product.delete(requestOptions);
            log.debug("Successfully deleted billing product : " + billingProductIdForApi + " of : " + apiName);
            //after deleting plans and the product, clean the database records
            stripeMonetizationDAO.deleteMonetizationData(apiId);
            log.debug("Successfully deleted monetization database records for : " + apiName);
        } catch (StripeException e) {
            String errorMessage = "Failed to delete products and plans in the billing engine.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to fetch database records when disabling monetization for : " + api.getId().getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get ID from database for : " + api.getId().getApiName() +
                    " when disabling monetization.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return true;
    }

    /**
     * Get mapping of tiers and billing engine plans
     *
     * @param api API
     * @return tier to billing plan mapping
     * @throws MonetizationException if failed to get tier to billing plan mapping
     */
    public Map<String, String> getMonetizedPoliciesToPlanMapping(API api) throws MonetizationException {

        try {
            String apiName = api.getId().getApiName();
            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getId(), null);
            //get billing engine product ID for that API
            String billingProductIdForApi = getBillingProductIdForApi(apiId);
            if (StringUtils.isEmpty(billingProductIdForApi)) {
                log.info("No product was found in billing engine for  : " + apiName);
                return new HashMap<String, String>();
            }
            //get tier to billing engine plan mapping
            return stripeMonetizationDAO.getTierToBillingEnginePlanMapping(apiId, billingProductIdForApi);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get tier to billing engine plan mapping for : " + api.getId().getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get ID from database for : " + api.getId().getApiName() +
                    " when getting tier to billing engine plan mapping.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
    }

    /**
     * Publish monetization usage count
     *
     * @param lastPublishInfo Info about last published task
     * @return true if successful, false otherwise
     * @throws MonetizationException if the action failed
     */
    public boolean publishMonetizationUsageRecords(MonetizationUsagePublishInfo lastPublishInfo)
            throws MonetizationException {

        String apiName = null;
        String apiVersion = null;
        String tenantDomain = null;
        int applicationId;
        String apiProvider = null;
        Long requestCount = 0L;
        Long currentTimestamp;
        int flag = 0;
        int counter = 0;
        JSONObject jsonObj = null;
        APIAdmin apiAdmin = new APIAdminImpl();

        DateFormat df = new SimpleDateFormat(StripeMonetizationConstants.TIME_FORMAT);
        Date dateobj = new Date();
        //get the time in UTC format
        df.setTimeZone(TimeZone.getTimeZone(StripeMonetizationConstants.TIME_ZONE));
        String currentDate = df.format(dateobj);
        currentTimestamp = getTimestamp(currentDate);
        try {
            //get the data from SP rest queries as a json object
            jsonObj = APIUtil.getUsageCountForMonetization(lastPublishInfo.getLastPublishTime(),
                    currentTimestamp);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get the usage count for monetization.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        log.info("Usage record publisher is running.");
        SubscriptionItem subscriptionItem = null;
        try {
            if (jsonObj != null) {
                JSONArray jArray = (JSONArray) jsonObj.get(APIConstants.Analytics.RECORDS_DELIMITER);
                for (Object record : jArray) {
                    JSONArray recordArray = (JSONArray) record;
                    // should return the 6 paramters derived below
                    if (recordArray.size() == 6) {
                        apiName = (String) recordArray.get(0);
                        apiVersion = (String) recordArray.get(1);
                        apiProvider = (String) recordArray.get(2);
                        tenantDomain = (String) recordArray.get(3);
                        applicationId = Integer.parseInt((String) recordArray.get(4));
                        requestCount = (Long) recordArray.get(5);
                        //get the billing engine subscription details
                        MonetizedSubscription subscription = stripeMonetizationDAO.getMonetizedSubscription(apiName,
                                apiVersion, apiProvider, applicationId, tenantDomain);
                        if (subscription.getSubscriptionId() != null) {
                            try {
                                //start the tenant flow to get the platform key
                                PrivilegedCarbonContext.startTenantFlow();
                                PrivilegedCarbonContext.getThreadLocalCarbonContext().
                                        setTenantDomain(tenantDomain, true);
                                //read tenant conf and get platform account key
                                Stripe.apiKey = getStripePlatformAccountKey(tenantDomain);
                            } catch (StripeMonetizationException e) {
                                String errorMessage = "Failed to get Stripe platform account key for tenant :  " +
                                        tenantDomain + " when disabling monetization for : " + apiName;
                                //throw MonetizationException as it will be logged and handled by the caller
                                throw new MonetizationException(errorMessage, e);
                            } finally {
                                PrivilegedCarbonContext.endTenantFlow();
                            }
                            String connectedAccountKey;
                            try {
                                PrivilegedCarbonContext.startTenantFlow();
                                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(
                                        tenantDomain, true);
                                apiProvider = APIUtil.replaceEmailDomain(apiProvider);
                                APIIdentifier identifier = new APIIdentifier(apiProvider, apiName, apiVersion);
                                APIProvider apiProvider1 = APIManagerFactory.getInstance().getAPIProvider(apiProvider);
                                API api = apiProvider1.getAPI(identifier);
                                Map<String, String> monetizationProperties = new Gson().fromJson(
                                        api.getMonetizationProperties().toString(), HashMap.class);
                                //get api publisher's stripe key (i.e - connected account key) from monetization
                                // properties in request payload
                                if (MapUtils.isNotEmpty(monetizationProperties) &&
                                        monetizationProperties.containsKey(
                                                StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
                                    connectedAccountKey = monetizationProperties.get
                                            (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
                                    if (StringUtils.isBlank(connectedAccountKey)) {
                                        String errorMessage = "Connected account stripe key was not found for : "
                                                + api.getId().getApiName();
                                        //throw MonetizationException as it will be logged and handled by the caller
                                        throw new MonetizationException(errorMessage);
                                    }
                                } else {
                                    String errorMessage = "Stripe key of the connected account is empty.";
                                    //throw MonetizationException as it will be logged and handled by the caller
                                    throw new MonetizationException(errorMessage);
                                }
                            } catch (APIManagementException e) {
                                String errorMessage = "Failed to get the Stripe key of the connected account from "
                                        + "the : " + apiName;
                                //throw MonetizationException as it will be logged and handled by the caller
                                throw new MonetizationException(errorMessage, e);
                            } finally {
                                PrivilegedCarbonContext.endTenantFlow();
                            }
                            RequestOptions subRequestOptions = RequestOptions.builder().
                                    setStripeAccount(connectedAccountKey).build();
                            Subscription sub = Subscription.retrieve(subscription.getSubscriptionId(),
                                    subRequestOptions);
                            //get the first subscription item from the array
                            subscriptionItem = sub.getItems().getData().get(0);
                            //check whether the billing plan is Usage Based.
                            if (subscriptionItem.getPlan().getUsageType().equals(
                                    StripeMonetizationConstants.METERED_PLAN)) {
                                flag++;
                                Map<String, Object> usageRecordParams = new HashMap<String, Object>();
                                usageRecordParams.put(StripeMonetizationConstants.QUANTITY, requestCount);
                                //provide the timesatmp in second format
                                usageRecordParams.put(StripeMonetizationConstants.TIMESTAMP,
                                        getTimestamp(currentDate) / 1000);
                                usageRecordParams.put(StripeMonetizationConstants.ACTION,
                                        StripeMonetizationConstants.INCREMENT);
                                RequestOptions usageRequestOptions = RequestOptions.builder().
                                        setStripeAccount(connectedAccountKey).setIdempotencyKey(subscriptionItem.getId()
                                        + lastPublishInfo.getLastPublishTime() + requestCount).build();
                                UsageRecord usageRecord = UsageRecord.createOnSubscriptionItem(
                                        subscriptionItem.getId(), usageRecordParams, usageRequestOptions);
                                //checks whether the usage record is published successfully
                                if (usageRecord.getId() != null) {
                                    counter++;
                                    if (log.isDebugEnabled()) {
                                        String msg = "Usage for " + apiName + " by Application with ID " + applicationId
                                                + " is successfully published to Stripe";
                                        log.info(msg);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (StripeMonetizationException e) {
            String errorMessage = "Unable to Publish usage Record to Billing Engine";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeException e) {
            String errorMessage = "Unable to Publish usage Record";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        // Flag equals counter when all the records are published successfully
        if (flag == counter) {
            try {
                //last publish time will be updated only if all the records are successfull
                lastPublishInfo.setLastPublishTime(currentTimestamp);
                lastPublishInfo.setState(StripeMonetizationConstants.COMPLETED);
                lastPublishInfo.setStatus(StripeMonetizationConstants.SUCCESSFULL);
                apiAdmin.updateMonetizationUsagePublishInfo(lastPublishInfo);
            } catch (APIManagementException ex) {
                String msg = "Failed to update last published time ";
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(msg, ex);
            }
            return true;
        }
        try {
            lastPublishInfo.setState(StripeMonetizationConstants.COMPLETED);
            lastPublishInfo.setStatus(StripeMonetizationConstants.UNSUCCESSFULL);
            apiAdmin.updateMonetizationUsagePublishInfo(lastPublishInfo);
        } catch (APIManagementException ex) {
            String msg = "Failed to update last published time ";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(msg, ex);
        }
        return false;
    }

    /**
     * Get current usage for a subscription
     *
     * @param subscriptionUUID subscription UUID
     * @param apiProvider      API provider
     * @return current usage for a subscription
     * @throws MonetizationException if failed to get current usage for a subscription
     */
    public Map<String, String> getCurrentUsageForSubscription(String subscriptionUUID, APIProvider apiProvider)
            throws MonetizationException {

        Map<String, String> billingEngineUsageData = new HashMap<String, String>();
        String apiName = null;
        try {
            SubscribedAPI subscribedAPI = ApiMgtDAO.getInstance().getSubscriptionByUUID(subscriptionUUID);
            APIIdentifier apiIdentifier = subscribedAPI.getApiId();
            APIProductIdentifier apiProductIdentifier;
            API api;
            APIProduct apiProduct;
            HashMap monetizationDataMap;
            int apiId;
            if (apiIdentifier != null) {
                api = apiProvider.getAPI(apiIdentifier);
                apiName = apiIdentifier.getApiName();
                if (api.getMonetizationProperties() == null) {
                    String errorMessage = "Monetization properties are empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                monetizationDataMap = new Gson().fromJson(api.getMonetizationProperties().toString(), HashMap.class);
                if (MapUtils.isEmpty(monetizationDataMap)) {
                    String errorMessage = "Monetization data map is empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                apiId = ApiMgtDAO.getInstance().getAPIID(apiIdentifier, null);
            } else {
                apiProductIdentifier = subscribedAPI.getProductId();
                apiProduct = apiProvider.getAPIProduct(apiProductIdentifier);
                apiName = apiProductIdentifier.getName();
                if (apiProduct.getMonetizationProperties() == null) {
                    String errorMessage = "Monetization properties are empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                monetizationDataMap = new Gson().fromJson(apiProduct.getMonetizationProperties().toString(), HashMap.class);
                if (MapUtils.isEmpty(monetizationDataMap)) {
                    String errorMessage = "Monetization data map is empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                apiId = ApiMgtDAO.getInstance().getAPIProductId(apiProductIdentifier);
            }
            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            //get billing engine platform account key
            String platformAccountKey = getStripePlatformAccountKey(tenantDomain);
            if (monetizationDataMap.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
                String connectedAccountKey = monetizationDataMap.get
                        (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY).toString();
                if (StringUtils.isBlank(connectedAccountKey)) {
                    String errorMessage = "Connected account stripe key was not found for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                Stripe.apiKey = platformAccountKey;
                //create request options to link with the connected account
                RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();
                int applicationId = subscribedAPI.getApplication().getId();
                String billingPlanSubscriptionId = stripeMonetizationDAO.getBillingEngineSubscriptionId(apiId, applicationId);
                Subscription billingEngineSubscription = Subscription.retrieve(billingPlanSubscriptionId, requestOptions);
                if (billingEngineSubscription == null) {
                    String errorMessage = "No billing engine subscription was found for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                //upcoming invoice is only applicable for metered usage (i.e - dynamic usage)
                if (!StripeMonetizationConstants.METERED_USAGE.equalsIgnoreCase
                        (billingEngineSubscription.getPlan().getUsageType())) {
                    String errorMessage = "Usage type should be set to 'metered' to get the pending bill.";
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                Map<String, Object> invoiceParams = new HashMap<String, Object>();
                invoiceParams.put("subscription", billingEngineSubscription.getId());
                //fetch the upcoming invoice
                Invoice invoice = Invoice.upcoming(invoiceParams, requestOptions);
                if (invoice == null) {
                    String errorMessage = "No billing engine subscription was found for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                //the below parameters are billing engine specific
                billingEngineUsageData.put("Description", invoice.getDescription());
                billingEngineUsageData.put("Paid", invoice.getPaid() != null ? invoice.getPaid().toString() : null);
                billingEngineUsageData.put("Tax", invoice.getTax() != null ?
                        invoice.getTax().toString() : null);
                billingEngineUsageData.put("Invoice ID", invoice.getId());
                billingEngineUsageData.put("Account Name", invoice.getAccountName());
                billingEngineUsageData.put("Next Payment Attempt", invoice.getNextPaymentAttempt() != null ?
                        dateFormatter.format(new Date(invoice.getNextPaymentAttempt() * 1000)) : null);
                billingEngineUsageData.put("Customer Email", invoice.getCustomerEmail());
                billingEngineUsageData.put("Currency", invoice.getCurrency());
                billingEngineUsageData.put("Account Country", invoice.getAccountCountry());
                billingEngineUsageData.put("Amount Remaining", invoice.getAmountRemaining() != null ?
                        Long.toString(invoice.getAmountRemaining() / 100L) : null);
                billingEngineUsageData.put("Period End", invoice.getPeriodEnd() != null ?
                        dateFormatter.format(new Date(invoice.getPeriodEnd() * 1000)) : null);
                billingEngineUsageData.put("Due Date", invoice.getDueDate() != null ?
                        dateFormatter.format(new Date(invoice.getDueDate())) : null);
                billingEngineUsageData.put("Amount Due", invoice.getAmountDue() != null ?
                        Long.toString(invoice.getAmountDue() / 100L) : null);
                billingEngineUsageData.put("Total Tax Amounts", invoice.getTotalTaxAmounts() != null ?
                        invoice.getTotalTaxAmounts().toString() : null);
                billingEngineUsageData.put("Amount Paid", invoice.getAmountPaid() != null ?
                        Long.toString(invoice.getAmountPaid() / 100L) : null);
                billingEngineUsageData.put("Subtotal", invoice.getSubtotal() != null ?
                        Long.toString(invoice.getSubtotal() / 100L) : null);
                billingEngineUsageData.put("Total", invoice.getTotal() != null ?
                        Long.toString(invoice.getTotal() / 100L) : null);
                billingEngineUsageData.put("Period Start", invoice.getPeriodStart() != null ?
                        dateFormatter.format(new Date(invoice.getPeriodStart() * 1000)) : null);

                //the below parameters are also returned from stripe, but commented for simplicity of the invoice
                /*billingEngineUsageData.put("object", "invoice");
                billingEngineUsageData.put("Application Fee Amount", invoice.getApplicationFeeAmount() != null ?
                        invoice.getApplicationFeeAmount().toString() : null);
                billingEngineUsageData.put("Attempt Count", invoice.getAttemptCount() != null ?
                        invoice.getAttemptCount().toString() : null);
                billingEngineUsageData.put("Attempted", invoice.getAttempted() != null ?
                        invoice.getAttempted().toString() : null);
                billingEngineUsageData.put("Billing", invoice.getBilling());
                billingEngineUsageData.put("Billing Reason", invoice.getBillingReason());
                billingEngineUsageData.put("Charge", invoice.getCharge());
                billingEngineUsageData.put("Created", invoice.getCreated() != null ? invoice.getCreated().toString() : null);
                billingEngineUsageData.put("Customer", invoice.getCustomer());
                billingEngineUsageData.put("Customer Address", invoice.getCustomerAddress() != null ?
                        invoice.getCustomerAddress().toString() : null);
                billingEngineUsageData.put("Customer Name", invoice.getCustomerName());
                billingEngineUsageData.put("Ending Balance", invoice.getEndingBalance() != null ?
                        invoice.getEndingBalance().toString() : null);
                billingEngineUsageData.put("Livemode", invoice.getLivemode() != null ? invoice.getLivemode().toString() : null);
                billingEngineUsageData.put("Number", invoice.getNumber());
                billingEngineUsageData.put("Payment Intent", invoice.getPaymentIntent());
                billingEngineUsageData.put("Post Payment Credit Notes Amount",
                        invoice.getPostPaymentCreditNotesAmount() != null ? invoice.getPostPaymentCreditNotesAmount().toString() : null);
                billingEngineUsageData.put("Pre Payment Credit Notes Amount",
                        invoice.getPrePaymentCreditNotesAmount() != null ? invoice.getPrePaymentCreditNotesAmount().toString() : null);
                billingEngineUsageData.put("Receipt Number", invoice.getReceiptNumber());
                billingEngineUsageData.put("Subscription", invoice.getSubscription());
                billingEngineUsageData.put("Tax Percent", invoice.getTaxPercent() != null ?
                        invoice.getTaxPercent().toString() : null);*/
            }
        } catch (StripeException e) {
            String errorMessage = "Error while fetching billing engine usage data for : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription details of : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get billing engine data for subscription : " + subscriptionUUID;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return billingEngineUsageData;
    }

    /**
     * Get total revenue for a given API from all subscriptions
     *
     * @param api         API
     * @param apiProvider API provider
     * @return total revenue data for a given API from all subscriptions
     * @throws MonetizationException if failed to get total revenue data for a given API
     */
    public Map<String, String> getTotalRevenue(API api, APIProvider apiProvider) throws MonetizationException {

        APIIdentifier apiIdentifier = api.getId();
        Map<String, String> revenueData = new HashMap<String, String>();
        try {
            //get all subscriptions for the API
            List<SubscribedAPI> apiUsages = apiProvider.getAPIUsageByAPIId(apiIdentifier);
            for (SubscribedAPI subscribedAPI : apiUsages) {
                //get subscription UUID for each subscription
                int subscriptionId = subscribedAPI.getSubscriptionId();
                String subscriptionUUID = stripeMonetizationDAO.getSubscriptionUUID(subscriptionId);
                //get revenue for each subscription and add them
                Map<String, String> billingEngineUsageData = getCurrentUsageForSubscription(subscriptionUUID, apiProvider);
                revenueData.put("Revenue for subscription ID : " + subscriptionId,
                        billingEngineUsageData.get("amount_due"));
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscriptions of : " + apiIdentifier.getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (StripeMonetizationException e) {
            String errorMessage = "Failed to get subscription UUID of : " + apiIdentifier.getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return revenueData;
    }

    /**
     * This method is used to get stripe platform account key for a given tenant
     *
     * @param tenantDomain tenant domain
     * @return stripe platform account key for the given tenant
     * @throws StripeMonetizationException if it fails to get stripe platform account key for the given tenant
     */
    private String getStripePlatformAccountKey(String tenantDomain) throws StripeMonetizationException {

        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);
            Registry configRegistry = ServiceReferenceHolder.getInstance().getRegistryService().
                    getConfigSystemRegistry(tenantId);
            if (configRegistry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = configRegistry.get(APIConstants.API_TENANT_CONF_LOCATION);
                String tenantConfContent = new String((byte[]) resource.getContent(), Charset.defaultCharset());
                if (StringUtils.isBlank(tenantConfContent)) {
                    String errorMessage = "Tenant configuration for tenant " + tenantDomain +
                            " cannot be empty when configuring monetization.";
                    throw new StripeMonetizationException(errorMessage);
                }
                //get the stripe key of platform account from  tenant conf json file
                JSONObject tenantConfig = (JSONObject) new JSONParser().parse(tenantConfContent);
                JSONObject monetizationInfo = (JSONObject) tenantConfig.get(StripeMonetizationConstants.MONETIZATION_INFO);
                String stripePlatformAccountKey = monetizationInfo.get
                        (StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY).toString();
                if (StringUtils.isBlank(stripePlatformAccountKey)) {
                    String errorMessage = "Stripe platform account key is empty for tenant : " + tenantDomain;
                    throw new StripeMonetizationException(errorMessage);
                }
                return stripePlatformAccountKey;
            }
        } catch (ParseException e) {
            String errorMessage = "Error while parsing tenant configuration in tenant : " + tenantDomain;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (UserStoreException e) {
            String errorMessage = "Failed to get the corresponding tenant configurations for tenant :  " + tenantDomain;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (RegistryException e) {
            String errorMessage = "Failed to get the configuration registry for tenant :  " + tenantDomain;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        }
        return StringUtils.EMPTY;
    }

    /**
     * Get billing product ID for a given API
     *
     * @param apiId API ID
     * @return billing product ID for the given API
     * @throws StripeMonetizationException if failed to get billing product ID for the given API
     */
    private String getBillingProductIdForApi(int apiId) throws StripeMonetizationException {

        String billingProductId = StringUtils.EMPTY;
        billingProductId = stripeMonetizationDAO.getBillingEngineProductId(apiId);
        return billingProductId;
    }

    /**
     * Get billing plan ID for a given tier
     *
     * @param apiId    API ID
     * @param tierName tier name
     * @return billing plan ID for a given tier
     * @throws StripeMonetizationException if failed to get billing plan ID for the given tier
     */
    private String getBillingPlanIdOfTier(int apiId, String tierName) throws StripeMonetizationException {

        String billingPlanId = StringUtils.EMPTY;
        billingPlanId = stripeMonetizationDAO.getBillingEnginePlanIdForTier(apiId, tierName);
        return billingPlanId;
    }

    /**
     * Create billing plan for a given commercial tier
     *
     * @param tier                tier
     * @param tenantId            tenant ID
     * @param platformAccountKey  billing engine platform account key
     * @param connectedAccountKey billing engine connected account key
     * @param billingProductId    billing engine product ID
     * @return created plan ID in billing engine
     * @throws StripeMonetizationException if fails to create billing plan
     */
    private String createBillingPlanForCommercialTier(Tier tier, int tenantId, String platformAccountKey,
                                                      String connectedAccountKey, String billingProductId)
            throws StripeMonetizationException {

        try {
            String tierUUID = ApiMgtDAO.getInstance().getSubscriptionPolicy(tier.getName(), tenantId).getUUID();
            //get plan ID from mapping table
            String planId = stripeMonetizationDAO.getBillingPlanId(tierUUID);
            Stripe.apiKey = platformAccountKey;
            //get that plan details
            Plan billingPlan = Plan.retrieve(planId);
            //get the values from that plan and replicate it
            Map<String, Object> planParams = new HashMap<String, Object>();
            planParams.put(StripeMonetizationConstants.AMOUNT, billingPlan.getAmount());
            planParams.put(StripeMonetizationConstants.BILLING_SCHEME, billingPlan.getBillingScheme());
            planParams.put(StripeMonetizationConstants.INTERVAL, billingPlan.getInterval());
            planParams.put(StripeMonetizationConstants.PRODUCT_NICKNAME, billingPlan.getNickname());
            planParams.put(StripeMonetizationConstants.PRODUCT, billingProductId);
            planParams.put(StripeMonetizationConstants.CURRENCY, billingPlan.getCurrency());
            planParams.put(StripeMonetizationConstants.USAGE_TYPE, billingPlan.getUsageType());
            RequestOptions planRequestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();
            //create a new stripe plan for the tier
            Plan createdPlan = Plan.create(planParams, planRequestOptions);
            return createdPlan.getId();
        } catch (StripeException e) {
            String errorMessage = "Unable to create billing plan for : " + tier.getName();
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get UUID for tier :  " + tier.getName();
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        }
    }

    /**
     * The method converts the date into timestamp
     *
     * @param date
     * @return Timestamp in long format
     */
    private long getTimestamp(String date) {

        SimpleDateFormat formatter = new SimpleDateFormat(StripeMonetizationConstants.TIME_FORMAT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        long time = 0;
        Date parsedDate = null;
        try {
            parsedDate = formatter.parse(date);
            time = parsedDate.getTime();
        } catch (java.text.ParseException e) {
            log.error("Error while parsing the date ", e);
        }
        return time;
    }
}
