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

package org.wso2.apim.monetization.impl.constants;

public class DatabaseConstants {
    public static final String GET_PLAN_INFO_FOR_API_AND_TIER = "SELECT MOESIF_PLAN_ID, MOESIF_PLAN_NAME, " +
            "MOESIF_PRICE_ID FROM AM_MONETIZATION_MOESIF WHERE API_ID = ? AND TIER_NAME = ?";

    public static final String INSERT_MONETIZATION_PLAN_DATA_SQL =
            "INSERT INTO AM_POLICY_PLAN_MAPPING (POLICY_UUID, PLAN_ID, PLAN_NAME, PRICE_ID,) VALUES (?,?,?,?)";

    public static final String INSERT_SUBSCRIPTION_DATA_SQL =
            " INSERT INTO AM_MONETIZATION_SUBSCRIPTION_MOESIF (SUBSCRIBED_API_ID, SUBSCRIBED_APPLICATION_ID," +
                    " TENANT_ID, CUSTOMER_ID, SUBSCRIPTION_ID)" +
                    " VALUES ((SELECT API_ID FROM AM_API WHERE API_UUID = ?),?,?,?,?)";

    public static final String GET_SUBSCRIPTION_UUID = "SELECT UUID FROM AM_SUBSCRIPTION WHERE SUBSCRIPTION_ID = ?";

    public static final String GET_BILLING_ENGINE_SUBSCRIPTION_ID = "SELECT SUBSCRIPTION_ID, CUSTOMER_ID FROM " +
                    "AM_MONETIZATION_SUBSCRIPTION_MOESIF " +
                    "WHERE SUBSCRIBED_APPLICATION_ID = ? AND SUBSCRIBED_API_ID = ?";

    public static final String GET_BILLING_PLANS_BY_API_ID = "SELECT TIER_NAME, MOESIF_PLAN_ID FROM AM_MONETIZATION_MOESIF " +
            "WHERE API_ID = ?";

}
