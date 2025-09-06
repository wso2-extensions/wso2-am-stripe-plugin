package org.wso2.apim.monetization.impl.workflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.stripe.Stripe;

import org.wso2.apim.monetization.impl.model.billing.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apim.monetization.impl.MoesifMonetizationException;
import org.wso2.apim.monetization.impl.MoesifMonetizationImpl;
import org.wso2.apim.monetization.impl.MonetizationDAO;
import org.wso2.apim.monetization.impl.StripeMonetizationException;
import org.wso2.apim.monetization.impl.billing.BillingEngine;
import org.wso2.apim.monetization.impl.billing.BillingEngineFactory;
import org.wso2.apim.monetization.impl.constants.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.enums.Provider;
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


//        Customer customer;
        MoesifPlanInfo moesifPlanInfo;
        String moesifApplicationKey;
        String priceId;
        String subscriptionId;

        try {
            Stripe.apiKey = MonetizationUtils.getPlatformAccountKey(tenantDomain);

            moesifApplicationKey = MonetizationUtils.getMoesifApplicationKey(tenantDomain);
//
            // Create customer in Stripe
//            customer = createStripeCustomer(workflowDTO);
//            log.info("Created Stripe customer [id: " + customer.getId() + ", name: " + customer.getName() + "]");

            // Create customer in billing engine
            Customer customer = new Customer();
            customer.setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());
            customer = billingEngine.createCustomer(customer);
            log.info("Created customer [id: " + customer.getId() + ", name: " + customer.getName() + "]");


//
//            // Register user in Moesif
            createUserInMoesif(customer, moesifApplicationKey);
            log.info("Created Moesif user for Stripe customer [id: " + customer.getId() + "]");
//
            // Fetch monetization plan info from DB
            try (Connection con = APIMgtDBUtil.getConnection()) {
                int apiId = ApiMgtDAO.getInstance().getAPIID(api.getUuid(), con);
                moesifPlanInfo = monetizationDAO.getPlanInfoForTier(apiId, subWorkFlowDTO.getTierName());
                priceId = moesifPlanInfo.getPriceId();
            }
//
//            // Create subscription in Stripe
//            subscriptionId = createMonetizedSubscriptions(priceId, customer);

            // Create subscription in billing engine
            Subscription subscription = billingEngine.createSubscription(customer, priceId);

//            subscriptionId = MoesifMonetizationImpl.createSubscription(
//                    ((SubscriptionWorkflowDTO) workflowDTO).getSubscriber(),priceId);

//            log.info("Created Stripe subscription [id: " + subscriptionId + "] for price [id: " + priceId + "]");

            monetizationDAO.addSubscription(identifier, subWorkFlowDTO.getApplicationId(), subWorkFlowDTO.getTenantId(),
                    customer.getId(), subscription.getId(), api.getUuid());

            // Register user in Moesif
            createUserInMoesif(customer, moesifApplicationKey);
            log.info("Created Moesif user for Stripe customer [id: " + customer.getId() + "]");

            // Create billing meter in Moesif
            createBillingMeterInMoesif(subscription.getId(), moesifPlanInfo, moesifApplicationKey);
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

//    /**
//     * Creates a Stripe customer for the given subscription workflow.
//     *
//     * @param workflowDTO The subscription workflow DTO containing subscriber details.
//     * @return The created Stripe Customer object.
//     * @throws StripeMonetizationException if customer creation fails.
//     */
//    private Customer createStripeCustomer(WorkflowDTO workflowDTO) throws StripeMonetizationException {
//        try {
//            // Build customer creation parameters
//            CustomerCreateParams params = CustomerCreateParams.builder()
//                    .setName(((SubscriptionWorkflowDTO) workflowDTO).getSubscriber())
//                    .build();
//
//            // Create customer in Stripe
//            Customer customer = Customer.create(params);
//
//            if (log.isDebugEnabled()) {
//                log.debug("Stripe customer creation request: " + params);
//                log.debug("Stripe customer creation response: " + customer);
//            }
//            log.info("Stripe customer created successfully for subscriber: "
//                    + ((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());
//
//            return customer;
//
//        } catch (Exception e) {
//            String errorMessage = String.format(
//                    "Error while creating Stripe customer for subscriber [%s]",
//                    ((SubscriptionWorkflowDTO) workflowDTO).getSubscriber());
//            log.error(errorMessage, e);
//            throw new StripeMonetizationException(errorMessage, e);
//        }
//    }

    /**
     * Creates a user in Moesif based on the given Stripe customer.
     *
     * @param customer             The Stripe customer object.
     * @param moesifApplicationKey The Moesif application key for authentication.
     * @throws MoesifMonetizationException if user creation fails.
     */
    private void createUserInMoesif(Customer customer, String moesifApplicationKey)
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


//    /**
//     * Creates a monetized subscription in Stripe for the given price and customer.
//     *
//     * @param priceId  The ID of the price to subscribe to.
//     * @param customer The Stripe customer object.
//     * @return The ID of the created subscription.
//     * @throws StripeMonetizationException if subscription creation fails.
//     */
//    public String createMonetizedSubscriptions(String priceId, Customer customer) throws StripeMonetizationException {
//        try {
//            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
//                    .setCustomer(customer.getId())
//                    .addItem(SubscriptionCreateParams.Item.builder()
//                            .setPrice(priceId)
//                            .build()
//                    ).build();
//            Subscription subscription = Subscription.create(params);
//
//            return subscription.getId();
//        } catch (Exception e) {
//            String errorMessage = String.format(
//                    "Error while creating subscription for customer: " + customer.getId() + " and price: " + priceId);
//            ;
//            throw new StripeMonetizationException(errorMessage, e);
//        }
//    }


    /**
     * Creates a billing meter in Moesif for the given subscription and plan info.
     *
     * @param subscriptionId The ID of the subscription.
     * @param moesifPlanInfo The Moesif plan information.
     * @param key            The Moesif application key for authentication.
     * @throws IOException            if an I/O exception occurs.
     * @throws APIManagementException if an error response is returned from the service.
     */
    public void createBillingMeterInMoesif(String subscriptionId, MoesifPlanInfo moesifPlanInfo, String key)
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


}
