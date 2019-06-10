/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import org.wso2.apim.monetization.impl.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.StripeMonetizationDAO;
import org.wso2.apim.monetization.impl.StripeMonetizationException;
import org.wso2.apim.monetization.impl.model.MonetizedSubscription;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowConstants;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple workflow executor for subscription delete action
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

    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {
        workflowDTO.setStatus(WorkflowStatus.APPROVED);
        complete(workflowDTO);
        super.publishEvents(workflowDTO);
        return new GeneralWorkflowResponse();
    }

    @Override
    public  WorkflowResponse deleteMonetizedSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {

        SubscriptionWorkflowDTO subWorkflowDTO;
        MonetizedSubscription monetizedSubscription;
        Stripe.apiKey = "sk_test_1Y8cd8EgnY1KYtBcs1vObHUF00020Je2H4";
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        StripeMonetizationDAO stripeMonetizationDAO =  new StripeMonetizationDAO();
        subWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;

        String connectedAccountKey = StringUtils.EMPTY;
        Map<String, String> monetizationProperties = new Gson().fromJson(api.getMonetizationProperties().toString(),
                HashMap.class);
        if (MapUtils.isNotEmpty(monetizationProperties) &&
                monetizationProperties.containsKey(StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY)) {
            connectedAccountKey = monetizationProperties.get
                    (StripeMonetizationConstants.BILLING_ENGINE_CONNECTED_ACCOUNT_KEY);
            if (StringUtils.isBlank(connectedAccountKey)) {
                String errorMessage = "Connected account stripe key was not found for API : " + api.getId().getApiName();
                log.error(errorMessage);
                throw new WorkflowException(errorMessage);
            }
        } else {
            String errorMessage = "Stripe key of the connected account is empty.";
            log.error(errorMessage);
            throw new WorkflowException(errorMessage);
        }

        RequestOptions requestOptions = RequestOptions.builder().setStripeAccount(connectedAccountKey).build();
        try {
            monetizedSubscription = stripeMonetizationDAO.getMonetizedSubscription(subWorkflowDTO.getApiName(),
                    subWorkflowDTO.getApiVersion(), subWorkflowDTO.getApiProvider(), subWorkflowDTO.getApplicationId(),
                    subWorkflowDTO.getTenantDomain());
        }catch (StripeMonetizationException ex){
            throw new WorkflowException("Could not complete subscription deletion workflow",ex);
        }

        if(monetizedSubscription.getSubscriptionId() != null){
            try {
                Subscription subscription = Subscription.retrieve(monetizedSubscription.getSubscriptionId(),
                        requestOptions);
                Map<String, Object> params = new HashMap<>();
                params.put("invoice_now", true);
                subscription = subscription.cancel(params,requestOptions);
                if(subscription.getStatus().equals("canceled")) {
                    stripeMonetizationDAO.removeMonetizedSubscription(monetizedSubscription.getId());
                }
                if(log.isDebugEnabled()){
                    String msg = "Monetized Subscriprion for API : "+subWorkflowDTO.getApiName()+" by Application : "
                            +subWorkflowDTO.getApplicationName()+ " is removed successfully ";
                    log.debug(msg);
                }
            }catch (StripeException ex)
            {
                String errorMessage = "Failed to remove Subcription in Billing Engine";
                log.error(errorMessage);
                throw new WorkflowException("Could not complete subscription deletion workflow for "
                        + subWorkflowDTO.getApiName(), ex);
            } catch (StripeMonetizationException ex){
                String errorMessage = "Failed to remove Subcription info from DB";
                log.error(errorMessage);
                throw new WorkflowException("Could not complete subscription deletion workflow for "
                        + subWorkflowDTO.getApiName(), ex);
            }
        }

        return  new GeneralWorkflowResponse();
    }

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
