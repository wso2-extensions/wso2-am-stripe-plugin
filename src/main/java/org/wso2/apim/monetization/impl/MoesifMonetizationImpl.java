/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apim.monetization.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.model.billing.SubscriptionInfo;
import org.wso2.apim.monetization.impl.util.MonetizationUtils;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.MonetizationException;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;


public class MoesifMonetizationImpl implements Monetization {
    private static final Log log = LogFactory.getLog(MoesifMonetizationImpl.class);

    private static final MonetizationDAO monetizationDAO = MonetizationDAO.getInstance();

    @Override
    public boolean createBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        //Billing plan creation for Moesif has moved to the enableMonetization method
        return false;
    }

    @Override
    public boolean updateBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    @Override
    public boolean deleteBillingPlan(SubscriptionPolicy subscriptionPolicy) throws MonetizationException {
        return false;
    }

    @Override
    public boolean enableMonetization(String tenantDomain, API api, Map<String, String> monetizationProperties) throws MonetizationException {
        // Retrieve Moesif application key
        String moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);

        try {
            String apiName = api.getId().getApiName();
            String apiVersion = api.getId().getVersion();
            String apiProvider = api.getId().getProviderName();
            String moesifPlanName = apiName + "-" + apiVersion + "-" + apiProvider;

            //creating a plan in Moesif which resembles the API in APIM
            String moesifPlanResponse = MonetizationUtils.createPlanInMoesif(api, moesifApplicationKey, moesifPlanName);

            String planId = MonetizationUtils.extractId(moesifPlanResponse);

            if (StringUtils.isNotBlank(planId)) {

                Map<String, String> tierPlanMap = new HashMap<String, String>();
                //scan for commercial tiers and add price to the above created plan
                for (Tier currentTier : api.getAvailableTiers()) {
                    if (APIConstants.COMMERCIAL_TIER_PLAN.equalsIgnoreCase(currentTier.getTierPlan())) {
                        if (StringUtils.isNotBlank(planId)) {
                            log.info("Current Tier " + currentTier);

                            //Create a price in Moesif for commercial tier
                            String priceResponse = MonetizationUtils.
                                    createPriceInMoesif(currentTier, planId, moesifApplicationKey, moesifPlanName);
                            log.info("Price Response: " + priceResponse);

                            if (StringUtils.isNotBlank(priceResponse)) {
                                String priceId = MonetizationUtils.extractId(priceResponse);
                                if (StringUtils.isNotBlank(priceId)) {
                                    tierPlanMap.put(currentTier.getName(), priceId);
                                    log.info("Monetization is enabled for the API: " + api.getId() +
                                            " with Tier: " + currentTier.getName() + " and Moesif Price ID: " + priceId);
                                } else {
                                    String errorMessage = "Failed to extract price_id from Moesif response";
                                    throw new MonetizationException(errorMessage);
                                }
                            }
                            try (Connection con = APIMgtDBUtil.getConnection()) {
                                int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
                                monetizationDAO.addMonetizationData(apiId, planId, moesifPlanName, tierPlanMap);
                            } catch (Exception e) {
                                String errorMessage = String.format(
                                        "Failed to persist monetization data for API [uuid: %s, planId: %s]",
                                        api.getUuid(), planId);
                                log.error(errorMessage, e);
                                throw new MoesifMonetizationException(errorMessage, e);
                            }

                        }
                    }
                }
            } else {
                throw new MonetizationException("Plan ID " + planId + " not yet available in Moesif");
            }

        } catch (MoesifMonetizationException e) {
            String errorMessage = "Failed to get Moesif account key for tenant :  " +
                    tenantDomain;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }

        return true;
    }

    @Override
    public boolean disableMonetization(String s, API api, Map<String, String> map) throws MonetizationException {
        return false;
    }

    @Override
    public Map<String, String> getMonetizedPoliciesToPlanMapping(API api) throws MonetizationException {

        try (Connection con = APIMgtDBUtil.getConnection()) {
            int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
            return MonetizationDAO.getTierToBillingPlanMapping(apiId);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get ID from database for : " + api.getId().getApiName() +
                    " when getting tier to billing engine plan mapping.";
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage = "Error while retrieving the API ID";
            throw new MonetizationException(errorMessage, e);
        }
    }


    /**
     * Fetches the current usage details for a given subscription from the billing engine.
     *
     * @param subscriptionUUID The UUID of the subscription for which to fetch usage details.
     * @param apiProvider      The APIProvider instance to retrieve API and subscription details.
     * @return A map containing usage details fetched from the billing engine.
     * @throws MonetizationException if any error occurs while fetching usage details.
     */
    @Override
    public Map<String, String> getCurrentUsageForSubscription(String subscriptionUUID, APIProvider apiProvider)
            throws MonetizationException {

        Map<String, String> billingEngineUsageData = new HashMap<String, String>();
        String apiName = null;
        try (Connection con = APIMgtDBUtil.getConnection()) {
            SubscribedAPI subscribedAPI = ApiMgtDAO.getInstance().getSubscriptionByUUID(subscriptionUUID);
            APIIdentifier apiIdentifier = subscribedAPI.getAPIIdentifier();
            APIProductIdentifier apiProductIdentifier;
            API api;
            APIProduct apiProduct;
            HashMap monetizationDataMap;
            int apiId;
            if (apiIdentifier != null) {
                api = apiProvider.getAPIbyUUID(apiIdentifier.getUUID(), apiIdentifier.getOrganization());
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
                apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
            } else {
                apiProductIdentifier = subscribedAPI.getProductId();
                apiProduct = apiProvider.getAPIProduct(apiProductIdentifier);
                apiName = apiProductIdentifier.getName();
                if (apiProduct.getMonetizationProperties() == null) {
                    String errorMessage = "Monetization properties are empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                monetizationDataMap = new Gson().fromJson(apiProduct.getMonetizationProperties().toString(),
                        HashMap.class);
                if (MapUtils.isEmpty(monetizationDataMap)) {
                    String errorMessage = "Monetization data map is empty for : " + apiName;
                    //throw MonetizationException as it will be logged and handled by the caller
                    throw new MonetizationException(errorMessage);
                }
                apiId = ApiMgtDAO.getInstance().getAPIProductId(apiProductIdentifier);
            }

            String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            int applicationId = subscribedAPI.getApplication().getId();

            SubscriptionInfo subscriptionInfo =
                    monetizationDAO.getMonetizedSubscription(apiId, applicationId);


            String moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);
            String moesifBillingReport = MonetizationUtils.getBillingReport(subscriptionInfo.getId(), moesifApplicationKey);

            JsonArray array = JsonParser.parseString(moesifBillingReport).getAsJsonArray();
            JsonObject billingReport = array.get(0).getAsJsonObject();

            if (billingReport == null) {
                String errorMessage = "No billing engine subscription was found for : " + apiName;
                //throw MonetizationException as it will be logged and handled by the caller
                throw new MonetizationException(errorMessage);
            }


            //ToDo:: Previously the invoice was fetched directly from the billing engine (ie: Stripe) and now we are
            // fetching billing report from Moesif. Hence need to check the below parameters and map accordingly.
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            //the below parameters are billing engine specific
//            billingEngineUsageData.put("Description", invoice.getDescription());
//            billingEngineUsageData.put("Paid", invoice.getAmountPaid() != null ? invoice.getAmountPaid().toString() : null);
//            billingEngineUsageData.put("Tax", invoice.getTa() != null ?
//                    invoice.getTax().toString() : null);
//            billingEngineUsageData.put("Invoice ID", get("currency").getAsString();;
//            billingEngineUsageData.put("Account Name", invoice.getAccountName());
//            billingEngineUsageData.put("Next Payment Attempt", invoice.getNextPaymentAttempt() != null ?
//                    dateFormatter.format(new Date(invoice.getNextPaymentAttempt() * 1000)) : null);
//            billingEngineUsageData.put("Customer Email", invoice.getCustomerEmail());
            billingEngineUsageData.put("Currency", billingReport.get("currency").getAsString());
//            billingEngineUsageData.put("Account Country", billingReport.get);
//            billingEngineUsageData.put("Amount Remaining", invoice.getAmountRemaining() != null ?
//                    Long.toString(invoice.getAmountRemaining() / 100L) : null);
//            billingEngineUsageData.put("Period End", invoice.getPeriodEnd() != null ?
//                    dateFormatter.format(new Date(invoice.getPeriodEnd() * 1000)) : null);
//            billingEngineUsageData.put("Due Date", invoice.getDueDate() != null ?
//                    dateFormatter.format(new Date(invoice.getDueDate())) : null);
//            billingEngineUsageData.put("Amount Due", invoice.getAmountDue() != null ?
//                    Long.toString(invoice.getAmountDue() / 100L) : null);
//            billingEngineUsageData.put("Total Tax Amounts", invoice.getTotalTaxes() != null ?
//                    invoice.getTotalTaxes().toString() : null);
//            billingEngineUsageData.put("Amount Paid", invoice.getAmountPaid() != null ?
//                    Long.toString(invoice.getAmountPaid() / 100L) : null);
//            billingEngineUsageData.put("Subtotal", invoice.getSubtotal() != null ?
//                    Long.toString(invoice.getSubtotal() / 100L) : null);
            billingEngineUsageData.put("Total Amount", billingReport.get("amount") != null ?
                    billingReport.get("amount").getAsString() : null);
            billingEngineUsageData.put("Period Start", billingReport.get("usage_start_time").getAsString() != null ?
                    billingReport.get("usage_start_time").getAsString() : null);
            billingEngineUsageData.put("Period End", billingReport.get("usage_end_time").getAsString() != null ?
                    billingReport.get("usage_end_time").getAsString() : null);
            billingEngineUsageData.put("Provider", billingReport.get("provider").getAsString() != null ?
                    billingReport.get("provider").getAsString() : null);
            billingEngineUsageData.put("Subscription ID", billingReport.get("subscription_id").getAsString() != null ?
                    billingReport.get("subscription_id").getAsString() : null);


        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription details of : " + apiName;
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        } catch (SQLException e) {
            String errorMessage = "Error while retrieving the API ID";
            throw new MonetizationException(errorMessage, e);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return billingEngineUsageData;
    }

    @Override
    public Map<String, String> getTotalRevenue(API api, APIProvider apiProvider) throws MonetizationException {
        APIIdentifier apiIdentifier = api.getId();
        Map<String, String> revenueData = new HashMap<String, String>();
        try {
            //get all subscriptions for the API
            List<SubscribedAPI> apiUsages = apiProvider.getAPIUsageByAPIId(api.getUuid(),
                    api.getId().getOrganization());
            for (SubscribedAPI subscribedAPI : apiUsages) {
                //get subscription UUID for each subscription
                int subscriptionId = subscribedAPI.getSubscriptionId();
                String subscriptionUUID = monetizationDAO.getSubscriptionUUID(subscriptionId);
                Map<String, String> billingEngineUsageData = getCurrentUsageForSubscription(subscriptionUUID,
                        apiProvider);
                revenueData.put("Revenue for subscription ID : " + subscriptionId,
                        billingEngineUsageData.get("amount_due"));
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscriptions of : " + apiIdentifier.getApiName();
            //throw MonetizationException as it will be logged and handled by the caller
            throw new MonetizationException(errorMessage, e);
        }
        return revenueData;
    }

    @Override
    public boolean publishMonetizationUsageRecords(MonetizationUsagePublishInfo monetizationUsagePublishInfo) throws MonetizationException {
        return false;
    }

}
