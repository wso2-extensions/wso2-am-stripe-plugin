package org.wso2.apim.monetization.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.policy.SubscriptionPolicy;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to handle database related actions when configuring monetization with stripe
 */
public class StripeMonetizationDAO {

    private static final Log log = LogFactory.getLog(StripeMonetizationDAO.class);
    private ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
    private static StripeMonetizationDAO INSTANCE = null;

    /**
     * Method to get the instance of the StripeMonetizationDAO.
     *
     * @return {@link StripeMonetizationDAO} instance
     */
    public static StripeMonetizationDAO getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StripeMonetizationDAO();
        }
        return INSTANCE;
    }

    /**
     * Add monetization plan data to the database
     *
     * @param policy    subscription policy
     * @param productId product id (in the billing engine)
     * @param planId    plan id (in the billing engine)
     * @throws StripeMonetizationException if failed to add monetization plan data to the database
     */
    public void addMonetizationPlanData(SubscriptionPolicy policy, String productId, String planId)
            throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement policyStatement = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            policyStatement = conn.prepareStatement(StripeMonetizationConstants.INSERT_MONETIZATION_PLAN_DATA_SQL);
            policyStatement.setString(1, apiMgtDAO.getSubscriptionPolicy(policy.getPolicyName(),
                    policy.getTenantId()).getUUID());
            policyStatement.setString(2, productId);
            policyStatement.setString(3, planId);
            policyStatement.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    String errorMessage = "Failed to rollback adding monetization plan for : " + policy.getPolicyName();
                    log.error(errorMessage);
                    throw new StripeMonetizationException(errorMessage, ex);
                }
            }
            String errorMessage = "Failed to add monetization plan for : " + policy.getPolicyName();
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription policy : " + policy.getPolicyName() +
                    " from database when creating stripe plan.";
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(policyStatement, conn, null);
        }
    }

    /**
     * Get plan data (in billing engine) for a given subscription policy
     *
     * @param policy subscription policy
     * @return plan data of subscription policy
     * @throws StripeMonetizationException if failed to get plan data
     */
    public Map<String, String> getPlanData(SubscriptionPolicy policy) throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        Map<String, String> planData = new HashMap<String, String>();
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(StripeMonetizationConstants.GET_BILLING_PLAN_DATA);
            ps.setString(1, apiMgtDAO.getSubscriptionPolicy(policy.getPolicyName(), policy.getTenantId()).getUUID());
            rs = ps.executeQuery();
            while (rs.next()) {
                planData.put(StripeMonetizationConstants.PRODUCT_ID, rs.getString("PRODUCT_ID"));
                planData.put(StripeMonetizationConstants.PLAN_ID, rs.getString("PLAN_ID"));
            }
        } catch (SQLException e) {
            String errorMessage = "Error while getting plan data for : " + policy.getPolicyName() + " policy.";
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription policy : " + policy.getPolicyName() +
                    " when getting plan data.";
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return planData;
    }

    /**
     * Update monetization plan data in the database
     *
     * @param policy    subscription policy
     * @param productId product id (in the billing engine)
     * @param planId    plan id (in the billing engine)
     * @throws StripeMonetizationException if failed to update monetization plan data to the database
     */
    public void updateMonetizationPlanData(SubscriptionPolicy policy, String productId, String planId)
            throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement policyStatement = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            policyStatement = conn.prepareStatement(StripeMonetizationConstants.UPDATE_MONETIZATION_PLAN_ID_SQL);
            policyStatement.setString(1, planId);
            policyStatement.setString(2, apiMgtDAO.getSubscriptionPolicy(policy.getPolicyName(),
                    policy.getTenantId()).getUUID());
            policyStatement.setString(3, productId);
            policyStatement.execute();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    String errorMessage = "Failed to rollback the update monetization plan action for policy : " +
                            policy.getPolicyName();
                    log.error(errorMessage);
                    throw new StripeMonetizationException(errorMessage, ex);
                }
            }
            String errorMessage = "Failed to update monetization plan for policy: " + policy;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get subscription policy : " + policy.getPolicyName() +
                    " when updating monetization plan data.";
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);

        } finally {
            APIMgtDBUtil.closeAllConnections(policyStatement, conn, null);
        }
    }

    /**
     * Delete monetization plan data from the database
     *
     * @param policy subscription policy
     * @throws StripeMonetizationException if failed to delete monetization plan data from the database
     */
    public void deleteMonetizationPlanData(SubscriptionPolicy policy) throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement policyStatement = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            policyStatement = conn.prepareStatement(StripeMonetizationConstants.DELETE_MONETIZATION_PLAN_DATA);
            policyStatement.setString(1, apiMgtDAO.getSubscriptionPolicy(policy.getPolicyName(),
                    policy.getTenantId()).getUUID());
            policyStatement.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    String errorMessage = "Failed to rollback the delete monetization plan action for policy : " +
                            policy.getPolicyName();
                    log.error(errorMessage);
                    throw new StripeMonetizationException(errorMessage, ex);
                }
            }
            String errorMessage = "Failed to delete the monetization plan action for policy : " + policy.getPolicyName();
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get policy : " + policy.getPolicyName() +
                    " when deleting monetization plan.";
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(policyStatement, conn, null);
        }
    }

    /**
     * This method is used to get the product id in the billing engine for a give API
     *
     * @param apiId API ID
     * @return billing engine product ID of the give API
     */
    public String getBillingEngineProductId(int apiId) throws StripeMonetizationException {

        String billingEngineProductId = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_BILLING_ENGINE_PRODUCT_BY_API);
            statement.setInt(1, apiId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                billingEngineProductId = rs.getString("STRIPE_PRODUCT_ID");
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get billing engine product ID of API : " + apiId;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return billingEngineProductId;
    }

    /**
     * Get billing plan ID for a given tier
     *
     * @param apiID    API ID
     * @param tierName tier name
     * @return billing plan ID for a given tier
     * @throws StripeMonetizationException if failed to get billing plan ID for a given tier
     */
    public String getBillingEnginePlanIdForTier(int apiID, String tierName) throws StripeMonetizationException {

        Connection connection = null;
        PreparedStatement statement = null;
        String billingEnginePlanId = StringUtils.EMPTY;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_BILLING_PLAN_FOR_TIER);
            statement.setInt(1, apiID);
            statement.setString(2, tierName);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                billingEnginePlanId = rs.getString("PLAN_ID");
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get billing plan ID tier : " + tierName;
            log.error(errorMessage, e);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return billingEnginePlanId;
    }

    /**
     * This method is used to add monetization data to the DB
     *
     * @param apiId       API ID
     * @param productId   stripe product ID
     * @param tierPlanMap stripe plan and tier mapping
     * @throws StripeMonetizationException if failed to add monetization data to the DB
     */
    public void addMonetizationData(int apiId, String productId, Map<String, String> tierPlanMap)
            throws StripeMonetizationException {

        PreparedStatement preparedStatement = null;
        Connection connection = null;
        boolean initialAutoCommit = false;
        try {
            if (!tierPlanMap.isEmpty()) {
                connection = APIMgtDBUtil.getConnection();
                preparedStatement = connection.prepareStatement(StripeMonetizationConstants.ADD_MONETIZATION_DATA_SQL);
                initialAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                for (Map.Entry<String, String> entry : tierPlanMap.entrySet()) {
                    preparedStatement.setInt(1, apiId);
                    preparedStatement.setString(2, entry.getKey());
                    preparedStatement.setString(3, productId);
                    preparedStatement.setString(4, entry.getValue());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            }
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException ex) {
                String errorMessage = "Failed to rollback add monetization data for API : " + apiId;
                log.error(errorMessage, e);
                throw new StripeMonetizationException(errorMessage, e);
            } finally {
                APIMgtDBUtil.setAutoCommit(connection, initialAutoCommit);
            }
        } finally {
            APIMgtDBUtil.closeAllConnections(preparedStatement, connection, null);
        }
    }

    /**
     * Get billing plan ID for a given tier
     *
     * @param tierUUID tier UUID
     * @return billing plan ID for a given tier
     * @throws StripeMonetizationException if failed to get billing plan ID for a given tier
     */
    public String getBillingPlanId(String tierUUID) throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String planId = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(StripeMonetizationConstants.GET_BILLING_PLAN_ID);
            ps.setString(1, tierUUID);
            rs = ps.executeQuery();
            while (rs.next()) {
                planId = rs.getString("PLAN_ID");
            }
        } catch (SQLException e) {
            String errorMessage = "Error while getting stripe plan ID for tier UUID : " + tierUUID;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return planId;
    }

    /**
     * Get subscription UUID given the subscription ID
     *
     * @param subscriptionId subscription ID
     * @return subscription UUID
     * @throws StripeMonetizationException if failed to get subscription UUID given the subscription ID
     */
    public String getSubscriptionUUID(int subscriptionId) throws StripeMonetizationException {

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        String planId = null;
        try {
            conn = APIMgtDBUtil.getConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(StripeMonetizationConstants.GET_SUBSCRIPTION_UUID);
            ps.setInt(1, subscriptionId);
            rs = ps.executeQuery();
            while (rs.next()) {
                planId = rs.getString("UUID");
            }
        } catch (SQLException e) {
            String errorMessage = "Error while getting UUID of subscription ID : " + subscriptionId;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(ps, conn, rs);
        }
        return planId;
    }

    /**
     * This method is used to get stripe plan and tier mapping
     *
     * @param apiID           API ID
     * @param stripeProductId stripe product ID
     * @return mapping between tier and stripe plans
     * @throws StripeMonetizationException if failed to get mapping between tier and stripe plans
     */
    public Map<String, String> getTierToBillingEnginePlanMapping(int apiID, String stripeProductId)
            throws StripeMonetizationException {

        Map<String, String> stripePlanTierMap = new HashMap<String, String>();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_BILLING_PLANS_BY_PRODUCT);
            statement.setInt(1, apiID);
            statement.setString(2, stripeProductId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String tierName = rs.getString("TIER_NAME");
                String stripePlanId = rs.getString("STRIPE_PLAN_ID");
                stripePlanTierMap.put(tierName, stripePlanId);
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get stripe plan and tier mapping for API : " + apiID;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return stripePlanTierMap;
    }

    /**
     * This method deletes monetization data for a given API from the DB
     *
     * @param apiId API ID
     * @throws StripeMonetizationException if failed to delete monetization data
     */
    public void deleteMonetizationData(int apiId) throws StripeMonetizationException {

        Connection connection = null;
        PreparedStatement statement = null;
        boolean initialAutoCommit = false;
        try {
            connection = APIMgtDBUtil.getConnection();
            statement = connection.prepareStatement(StripeMonetizationConstants.DELETE_MONETIZATION_DATA_SQL);
            initialAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            statement.setInt(1, apiId);
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException ex) {
                String errorMessage = "Failed to delete monetization data for API : " + apiId;
                log.error(errorMessage);
                throw new StripeMonetizationException(errorMessage, e);
            } finally {
                APIMgtDBUtil.setAutoCommit(connection, initialAutoCommit);
            }
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
    }

    /**
     * Get Billing Engine Subscription ID
     *
     * @param apiId         API ID
     * @param applicationId Application ID
     * @return Billing Engine Subscription ID
     * @throws StripeMonetizationException If Failed To Get Billing Engine Subscription ID
     */
    public String getBillingEngineSubscriptionId(int apiId, int applicationId) throws StripeMonetizationException {

        String billingEngineSubscriptionId = null;
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = APIMgtDBUtil.getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(StripeMonetizationConstants.GET_BILLING_ENGINE_SUBSCRIPTION_ID);
            statement.setInt(1, applicationId);
            statement.setInt(2, apiId);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                billingEngineSubscriptionId = rs.getString("SUBSCRIPTION_ID");
            }
            connection.commit();
        } catch (SQLException e) {
            String errorMessage = "Failed to get billing engine subscription ID of API : " + apiId +
                    " and application ID : " + applicationId;
            log.error(errorMessage);
            throw new StripeMonetizationException(errorMessage, e);
        } finally {
            APIMgtDBUtil.closeAllConnections(statement, connection, null);
        }
        return billingEngineSubscriptionId;
    }

}
