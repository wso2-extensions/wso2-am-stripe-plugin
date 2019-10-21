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

package org.wso2.apim.monetization.impl.workflow;

import com.google.gson.Gson;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.net.RequestOptions;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.wso2.apim.monetization.impl.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.StripeMonetizationDAO;
import org.wso2.apim.monetization.impl.StripeMonetizationException;
import org.wso2.apim.monetization.impl.model.MonetizedSubscription;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIProduct;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowConstants;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * worrkflow executor for stripe based subscription delete action
 */
public class StripeSubscriptionDeletionWorkflowExecutor extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(StripeSubscriptionDeletionWorkflowExecutor.class);

    @Override
    public String getWorkflowType() {
        return WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_DELETION;
    }

    @Override
    public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException {
        // implemetation is not provided in this version
        return null;
    }

    /**
     * This method executes subscription deletion workflow and return workflow response back to the caller
     *
     * @param workflowDTO The WorkflowDTO which contains workflow contextual information related to the workflow
     * @return workflow response back to the caller
     * @throws WorkflowException Thrown when the workflow execution was not fully performed
     */
    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {

        workflowDTO.setStatus(WorkflowStatus.APPROVED);
        complete(workflowDTO);
        super.publishEvents(workflowDTO);
        return new GeneralWorkflowResponse();
    }

    /**
     * This method executes monetization related functions in the subscription deletion workflow
     *
     * @param workflowDTO The WorkflowDTO which contains workflow contextual information related to the workflow
     * @param api         API
     * @return workflow response back to the caller
     * @throws WorkflowException Thrown when the workflow execution was not fully performed
     */
    @Override
    public WorkflowResponse deleteMonetizedSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {

        SubscriptionWorkflowDTO subWorkflowDTO;
        MonetizedSubscription monetizedSubscription;
        StripeMonetizationDAO stripeMonetizationDAO = new StripeMonetizationDAO();
        subWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        //read the platform key of Stripe
        Stripe.apiKey = getPlatformAccountKey(subWorkflowDTO.getTenantId());
        String connectedAccountKey = StringUtils.EMPTY;
        Map<String, String> monetizationProperties = new Gson().fromJson(api.getMonetizationProperties().toString(),
                HashMap.class);
        if (MapUtils.isNotEmpty(monetizationProperties) &&
                monetizationProperties.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
            // get the key of the connected account
            connectedAccountKey = monetizationProperties.get
                    (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
            if (StringUtils.isBlank(connectedAccountKey)) {
                String errorMessage = "Connected account stripe key was not found for : "
                        + api.getId().getApiName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage);
            }
        } else {
            String errorMessage = "Stripe key of the connected account is empty.";
            log.error(errorMessage);
            throw new WorkflowException(errorMessage);
        }
        //needed to add,remove artifacts in connected account
        RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();
        try {
            //get the stripe subscription id
            monetizedSubscription = stripeMonetizationDAO.getMonetizedSubscription(subWorkflowDTO.getApiName(),
                    subWorkflowDTO.getApiVersion(), subWorkflowDTO.getApiProvider(), subWorkflowDTO.getApplicationId(),
                    subWorkflowDTO.getTenantDomain());
        } catch (StripeMonetizationException ex) {
            String errorMessage = "Could not retrieve monetized subscription info for : "
                    + subWorkflowDTO.getApplicationName() + " by Application : " + subWorkflowDTO.getApplicationName();
            throw new WorkflowException(errorMessage, ex);
        }
        if (monetizedSubscription.getSubscriptionId() != null) {
            try {
                Subscription subscription = Subscription.retrieve(monetizedSubscription.getSubscriptionId(),
                        requestOptions);
                Map<String, Object> params = new HashMap<>();
                //canceled subscription will be invoiced immediately
                params.put(StripeMonetizationConstants.INVOICE_NOW, true);
                subscription = subscription.cancel(params, requestOptions);
                if (StringUtils.equals(subscription.getStatus(), StripeMonetizationConstants.CANCELED)) {
                    stripeMonetizationDAO.removeMonetizedSubscription(monetizedSubscription.getId());
                }
                if (log.isDebugEnabled()) {
                    String msg = "Monetized subscriprion for : " + subWorkflowDTO.getApiName()
                            + " by Application : " + subWorkflowDTO.getApplicationName() + " is removed successfully ";
                    log.debug(msg);
                }
            } catch (StripeException ex) {
                String errorMessage = "Failed to remove subcription in billing engine for : "
                        + subWorkflowDTO.getApiName() + " by Application : " + subWorkflowDTO.getApplicationName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage, ex);
            } catch (StripeMonetizationException ex) {
                String errorMessage = "Failed to remove monetization subcription info from DB of : "
                        + subWorkflowDTO.getApiName() + " by Application : " + subWorkflowDTO.getApplicationName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage, ex);
            }
        }
        return execute(workflowDTO);
    }

    @Override
    public WorkflowResponse deleteMonetizedSubscription(WorkflowDTO workflowDTO, APIProduct apiProduct)
            throws WorkflowException {

        SubscriptionWorkflowDTO subWorkflowDTO;
        MonetizedSubscription monetizedSubscription;
        StripeMonetizationDAO stripeMonetizationDAO = new StripeMonetizationDAO();
        subWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        //read the platform key of Stripe
        Stripe.apiKey = getPlatformAccountKey(subWorkflowDTO.getTenantId());
        String connectedAccountKey = StringUtils.EMPTY;
        Map<String, String> monetizationProperties = new Gson().fromJson(apiProduct.getMonetizationProperties().toString(),
                HashMap.class);
        if (MapUtils.isNotEmpty(monetizationProperties) &&
                monetizationProperties.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
            // get the key of the connected account
            connectedAccountKey = monetizationProperties.get
                    (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
            if (StringUtils.isBlank(connectedAccountKey)) {
                String errorMessage = "Connected account stripe key was not found for : " + apiProduct.getId().getName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage);
            }
        } else {
            String errorMessage = "Stripe key of the connected account is empty.";
            log.error(errorMessage);
            throw new WorkflowException(errorMessage);
        }
        //needed to add,remove artifacts in connected account
        RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();
        try {
            //get the stripe subscription id
            monetizedSubscription = stripeMonetizationDAO.getMonetizedSubscription(subWorkflowDTO.getApiName(),
                    subWorkflowDTO.getApiVersion(), subWorkflowDTO.getApiProvider(), subWorkflowDTO.getApplicationId(),
                    subWorkflowDTO.getTenantDomain());
        } catch (StripeMonetizationException ex) {
            String errorMessage = "Could not retrieve monetized subscription info for : "
                    + subWorkflowDTO.getApplicationName() + " by application : " + subWorkflowDTO.getApplicationName();
            throw new WorkflowException(errorMessage, ex);
        }
        if (monetizedSubscription.getSubscriptionId() != null) {
            try {
                Subscription subscription = Subscription.retrieve(monetizedSubscription.getSubscriptionId(),
                        requestOptions);
                Map<String, Object> params = new HashMap<>();
                //canceled subscription will be invoiced immediately
                params.put(StripeMonetizationConstants.INVOICE_NOW, true);
                subscription = subscription.cancel(params, requestOptions);
                if (StringUtils.equals(subscription.getStatus(), StripeMonetizationConstants.CANCELED)) {
                    stripeMonetizationDAO.removeMonetizedSubscription(monetizedSubscription.getId());
                }
                if (log.isDebugEnabled()) {
                    String msg = "Monetized subscriprion for : " + subWorkflowDTO.getApiName()
                            + " by application : " + subWorkflowDTO.getApplicationName() + " is removed successfully ";
                    log.debug(msg);
                }
            } catch (StripeException ex) {
                String errorMessage = "Failed to remove subcription in billing engine for : "
                        + subWorkflowDTO.getApiName() + " by Application : " + subWorkflowDTO.getApplicationName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage, ex);
            } catch (StripeMonetizationException ex) {
                String errorMessage = "Failed to remove monetization subcription info from DB of : "
                        + subWorkflowDTO.getApiName() + " by Application : " + subWorkflowDTO.getApplicationName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage, ex);
            }
        }
        return execute(workflowDTO);
    }

    /**
     * Returns the stripe key of the platform/tenant
     *
     * @param tenantId id of the tenant
     * @return the stripe key of the platform/tenant
     * @throws WorkflowException
     */
    private String getPlatformAccountKey(int tenantId) throws WorkflowException {

        String stripePlatformAccountKey = null;
        try {
            Registry configRegistry = ServiceReferenceHolder.getInstance().getRegistryService().getConfigSystemRegistry(
                    tenantId);
            if (configRegistry.resourceExists(APIConstants.API_TENANT_CONF_LOCATION)) {
                Resource resource = configRegistry.get(APIConstants.API_TENANT_CONF_LOCATION);
                String content = new String((byte[]) resource.getContent(), Charset.defaultCharset());

                if (StringUtils.isBlank(content)) {
                    String errorMessage = "Tenant configuration cannot be empty when configuring monetization.";
                    throw new WorkflowException(errorMessage);
                }
                //get the stripe key of patform account from tenant conf file
                JSONObject tenantConfig = (JSONObject) new JSONParser().parse(content);
                JSONObject monetizationInfo = (JSONObject) tenantConfig.get(
                        StripeMonetizationConstants.MONETIZATION_INFO);
                stripePlatformAccountKey = monetizationInfo.get(
                        StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY).toString();

                if (StringUtils.isBlank(stripePlatformAccountKey)) {
                    throw new WorkflowException("stripePlatformAccountKey is empty!!!");
                }
            }
        } catch (RegistryException ex) {
            throw new WorkflowException("Could not get all registry objects : ", ex);
        } catch (org.json.simple.parser.ParseException ex) {
            throw new WorkflowException("Could not get Stripe Platform key : ", ex);
        }
        return stripePlatformAccountKey;
    }

    /**
     * This method completes subscription creation workflow and return workflow response back to the caller
     *
     * @param workflowDTO The WorkflowDTO which contains workflow contextual information related to the workflow
     * @return workflow response back to the caller
     * @throws WorkflowException
     */
    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {

        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        SubscriptionWorkflowDTO subWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String errorMsg = null;

        try {
            APIIdentifier identifier = new APIIdentifier(subWorkflowDTO.getApiProvider(),
                    subWorkflowDTO.getApiName(), subWorkflowDTO.getApiVersion());

            apiMgtDAO.removeSubscription(identifier, ((SubscriptionWorkflowDTO) workflowDTO).getApplicationId());
        } catch (APIManagementException e) {
            errorMsg = "Could not complete subscription deletion workflow for api: " + subWorkflowDTO.getApiName();
            throw new WorkflowException(errorMsg, e);
        }
        return new GeneralWorkflowResponse();
    }
}
