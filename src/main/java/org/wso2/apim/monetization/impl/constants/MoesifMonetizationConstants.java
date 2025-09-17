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

public class MoesifMonetizationConstants {
    public static final String MONETIZATION_INFO = "MonetizationInfo";
    public static final String MOESIF_APPLICATION_KEY = "MoesifApplicationKey";
    public static final String BILLING_PLANS_URL = "https://api.moesif.com/v1/~/billing/catalog/plans?provider=%s";
    public static final String BILLING_PRICE_URL = "https://api.moesif.com/v1/~/billing/catalog/prices?provider=%s";
    public static final String MOESIF_USER_URL = "https://api.moesif.com/v1/search/~/users";
    public static final String BILLING_METER_URL = "https://api.moesif.com/v1/~/billing/meters";
    public static final String BILLING_REPORT_URL = "https://api.moesif.com/v1/~/billing/reports";
    public static final String BILLING_PLAN_STATUS_ACTIVE = "active";
    public static final String BILLING_METER_STATUS_ACTIVE = "active";
    public static final String BILLING_PRICE_STATUS_ACTIVE = "active";


}
