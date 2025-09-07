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

package org.wso2.apim.monetization.impl.model;

public class MoesifPlanInfo {
    private String planId;
    private String priceId;
    private String planName;

    public MoesifPlanInfo(String planId, String priceId, String planName) {
        this.planId = planId;
        this.priceId = priceId;
        this.planName = planName;
    }

    public String getPlanId() {
        return planId;
    }

    public String getPriceId() {
        return priceId;
    }

    public String getPlanName() {
        return planName;
    }
}
