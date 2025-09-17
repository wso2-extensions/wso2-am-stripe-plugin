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

package org.wso2.apim.monetization.impl.constants;

public class StripeMonetizationConstants {
    public static final String MONETIZATION_INFO = "MonetizationInfo";
    public static final String BILLING_ENGINE_PLATFORM_ACCOUNT_KEY = "BillingEnginePlatformAccountKey";

    public static final String ADD_MONETIZATION_DATA_SQL = "INSERT INTO AM_MONETIZATION_MOESIF VALUES (?,?,?,?,?)";

    public static final String GET_PRICE_ID_FOR_API_AND_TIER = "SELECT MOESIF_PRICE_ID FROM AM_MONETIZATION_MOESIF " +
            "WHERE API_ID = ? AND TIER_NAME = ?";


}
