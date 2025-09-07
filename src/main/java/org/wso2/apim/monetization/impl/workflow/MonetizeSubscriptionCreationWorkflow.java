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

package org.wso2.apim.monetization.impl.workflow;

import com.stripe.Stripe;

import org.wso2.apim.monetization.impl.model.billing.SubscriptionInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.MoesifMonetizationException;
import org.wso2.apim.monetization.impl.MonetizationDAO;
import org.wso2.apim.monetization.impl.billing.BillingEngine;
import org.wso2.apim.monetization.impl.billing.BillingEngineFactory;
import org.wso2.apim.monetization.impl.model.MoesifPlanInfo;
import org.wso2.apim.monetization.impl.model.billing.Customer;
import org.wso2.apim.monetization.impl.util.MonetizationUtils;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.WorkflowResponse;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SubscriptionWorkflowDTO;
import org.wso2.carbon.apimgt.impl.dto.WorkflowDTO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.workflow.GeneralWorkflowResponse;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowExecutor;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowStatus;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;


public class MonetizeSubscriptionCreationWorkflow extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(MonetizeSubscriptionCreationWorkflow.class);
    private final MonetizationDAO monetizationDAO = MonetizationDAO.getInstance();
    private final BillingEngine billingEngine = BillingEngineFactory.getBillingEngine();

    @Override
    public String getWorkflowType() {
        return null;
    }

    @Override
    public List<WorkflowDTO> getWorkflowDetails(String s) throws WorkflowException {
        return null;
    }

    @Override
    public WorkflowResponse execute(WorkflowDTO workflowDTO) throws WorkflowException {

        SubscriptionWorkflowDTO subsWorkflowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        workflowDTO.setProperties("apiName", subsWorkflowDTO.getApiName());
        workflowDTO.setProperties("apiVersion", subsWorkflowDTO.getApiVersion());
        workflowDTO.setProperties("subscriber", subsWorkflowDTO.getSubscriber());
        workflowDTO.setProperties("applicationName", subsWorkflowDTO.getApplicationName());
        super.execute(workflowDTO);
        workflowDTO.setStatus(WorkflowStatus.APPROVED);
        WorkflowResponse workflowResponse = complete(workflowDTO);

        return workflowResponse;
    }

    @Override
    public WorkflowResponse complete(WorkflowDTO workflowDTO) throws WorkflowException {

        workflowDTO.setUpdatedTime(System.currentTimeMillis());
        super.complete(workflowDTO);
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        try {
            apiMgtDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                    APIConstants.SubscriptionStatus.UNBLOCKED);
        } catch (APIManagementException e) {
            throw new WorkflowException("Could not complete subscription creation workflow", e);
        }
        return new GeneralWorkflowResponse();
    }

    /**
     * Handles subscription monetization workflow.
     *
     * @param workflowDTO Workflow details
     * @param api         API for which monetization is enabled
     * @return WorkflowResponse indicating success/failure
     * @throws WorkflowException if monetization setup fails
     */
    @Override
    public WorkflowResponse monetizeSubscription(WorkflowDTO workflowDTO, API api) throws WorkflowException {

        SubscriptionWorkflowDTO subWorkFlowDTO = (SubscriptionWorkflowDTO) workflowDTO;
        String tenantDomain = workflowDTO.getTenantDomain();
        APIIdentifier identifier = new APIIdentifier(subWorkFlowDTO.getApiProvider(), subWorkFlowDTO.getApiName(),
                subWorkFlowDTO.getApiVersion());

        MoesifPlanInfo moesifPlanInfo;
        String moesifApplicationKey;
        String priceId;

        try {
            Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(tenantDomain);
            moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);

            // Create customer in billing engine
            Customer customer = new Customer();
            customer.setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());
            customer = billingEngine.createCustomer(customer);
            log.info("Created customer [id: " + customer.getId() + ", name: " + customer.getName() + "]");

            // Register user in Moesif
            MonetizationUtils.createUserInMoesif(customer, moesifApplicationKey);
            log.info("Created Moesif user for Stripe customer [id: " + customer.getId() + "]");

            // Fetch monetization plan info from DB
            try (Connection con = APIMgtDBUtil.getConnection()) {
                int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
                moesifPlanInfo = monetizationDAO.getPlanInfoForTier(apiId, subWorkFlowDTO.getTierName());
                priceId = moesifPlanInfo.getPriceId();
            }

            // Create subscription in billing engine
            SubscriptionInfo subscription = billingEngine.createSubscription(customer, priceId);

            monetizationDAO.addSubscription(identifier, subWorkFlowDTO.getApplicationId(), subWorkFlowDTO.getTenantId(),
                    customer.getId(), subscription.getId(), api.getUuid());

            // Create billing meter in Moesif
            MonetizationUtils.createBillingMeterInMoesif(subscription.getId(), moesifPlanInfo, moesifApplicationKey);
            log.info("Created Moesif billing meter for subscription [id: " + subscription.getId() + "]");

        } catch (SQLException | APIManagementException e) {
            String msg = "Error while accessing API management DB during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (IOException e) {
            String msg = "Error while creating billing meter in Moesif";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (MoesifMonetizationException e) {
            String msg = "Error while interacting with Moesif during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);

        } catch (Exception e) {
            String msg = "Unexpected error during subscription monetization";
            log.error(msg, e);
            throw new WorkflowException(msg, e);
        }

        return execute(workflowDTO);
    }


}
