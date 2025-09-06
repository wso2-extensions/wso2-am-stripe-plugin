package org.wso2.apim.monetization.impl.util;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.wso2.apim.monetization.impl.MoesifMonetizationException;
import org.wso2.apim.monetization.impl.constants.MoesifMonetizationConstants;
import org.wso2.apim.monetization.impl.constants.StripeMonetizationConstants;
import org.wso2.apim.monetization.impl.enums.Provider;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.workflow.WorkflowException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MonetizationUtils {

    private static final Log log = LogFactory.getLog(MonetizationUtils.class);

    /**
     * Get the platform account key of the tenant
     *
     * @param tenantDomain tenant domain of the user
     * @return platform account key
     * @throws WorkflowException if failed to get the platform account key
     */
    public static String getPlatformAccountKey(String tenantDomain) throws WorkflowException {

        String stripePlatformAccountKey = null;
        try {
            //get the stripe key of platform account from  tenant conf json file
            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);
            if (tenantConfig.containsKey(StripeMonetizationConstants.MONETIZATION_INFO)) {
                JSONObject monetizationInfo = (JSONObject) tenantConfig
                        .get(StripeMonetizationConstants.MONETIZATION_INFO);
                if (monetizationInfo.containsKey(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY)) {
                    stripePlatformAccountKey = monetizationInfo
                            .get(StripeMonetizationConstants.BILLING_ENGINE_PLATFORM_ACCOUNT_KEY).toString();
                    if (StringUtils.isBlank(stripePlatformAccountKey)) {
                        String errorMessage = "Stripe platform account key is empty for tenant : " + tenantDomain;
                        throw new WorkflowException(errorMessage);
                    }
                    return stripePlatformAccountKey;
                }
            }
        } catch (APIManagementException e) {
            throw new WorkflowException("Failed to get the configuration for tenant from DB:  " + tenantDomain, e);
        }

        return stripePlatformAccountKey;
    }

    /**
     * Get the Moesif application key of the tenant
     *
     * @param tenantDomain tenant domain of the user
     * @return Moesif application key
     * @throws MoesifMonetizationException if failed to get the Moesif application key
     */
    public static String getMoesifApplicationKey(String tenantDomain) throws MoesifMonetizationException {

        try {
            //get the application key of platform account from tenant conf json file
            JSONObject tenantConfig = APIUtil.getTenantConfig(tenantDomain);

            if (tenantConfig.containsKey(MoesifMonetizationConstants.MONETIZATION_INFO)) {
                JSONObject monetizationInfo = (JSONObject) tenantConfig
                        .get(MoesifMonetizationConstants.MONETIZATION_INFO);
                if (monetizationInfo.containsKey(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY)) {
                    String moesifApplicationKey = monetizationInfo
                            .get(MoesifMonetizationConstants.MOESIF_APPLICATION_KEY).toString();
                    if (StringUtils.isBlank(moesifApplicationKey)) {
                        String errorMessage = "Moesif application key is empty for tenant : " + tenantDomain;
                        throw new MoesifMonetizationException(errorMessage);
                    }
                    return moesifApplicationKey;
                }
            }
        } catch (APIManagementException e) {
            String errorMessage = "Failed to get the configuration for tenant from DB:  " + tenantDomain;
            log.error(errorMessage);
            throw new MoesifMonetizationException(errorMessage, e);
        }
        return StringUtils.EMPTY;
    }

    /**
     * Invoke a REST API service
     *
     * @param url     URL of the service
     * @param payload Payload to be sent to the service
     * @param token   Bearer token if any
     * @return Response of the service
     * @throws IOException              if an I/O exception occurs
     * @throws APIManagementException   if an error response is returned from the service
     */
    public static String invokeService(String url, String payload, String token) throws IOException, APIManagementException {
        HttpClient httpClient = APIUtil.getHttpClient(url); // keep pooled client

        HttpPost post = new HttpPost(url);
        post.setHeader(APIConstants.HEADER_CONTENT_TYPE, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        post.setHeader(APIConstants.HEADER_ACCEPT, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        if (token != null && !token.isEmpty()) {
            post.setHeader("Authorization", "Bearer " + token);
        }

        if (payload != null) {
            post.setEntity(new StringEntity(payload, "UTF-8"));
        }

        try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new APIManagementException("Moesif call failed [" + statusCode + "] " + responseBody);
            }
        }
    }

    public static String invokeService(String url, String method, String payload, String token)
            throws IOException, APIManagementException {

        HttpClient httpClient = APIUtil.getHttpClient(url); // pooled client
        HttpUriRequest request;

        switch (method.toUpperCase()) {
            case "POST":
                HttpPost post = new HttpPost(url);
                if (payload != null) {
                    post.setEntity(new StringEntity(payload, "UTF-8"));
                }
                request = post;
                break;

            case "PUT":
                HttpPut put = new HttpPut(url);
                if (payload != null) {
                    put.setEntity(new StringEntity(payload, "UTF-8"));
                }
                request = put;
                break;

            case "DELETE":
                request = new HttpDelete(url);
                break;

            case "GET":
            default:
                request = new HttpGet(url);
                break;
        }

        // Common headers
        request.setHeader(APIConstants.HEADER_CONTENT_TYPE, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        request.setHeader(APIConstants.HEADER_ACCEPT, APIConstants.APPLICATION_JSON_MEDIA_TYPE);
        if (token != null && !token.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + token);
        }

        try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            } else {
                throw new APIManagementException("Moesif call failed [" + statusCode + "] " + responseBody);
            }
        }
    }



    /***
     * Construct the URL according to the provider
     *
     *
     * @param URL
     * @param provider
     * @return String formatted URL
     */
    public static String constructProviderURL(String URL, Provider provider) {
        return String.format(URL, provider.getValue());
    }

    public static String getBillingReport(String subscriptionId, String token)
            throws IOException, APIManagementException {

        String url = MoesifMonetizationConstants.BILLING_REPORT_URL + "?subscription_id=" +
                URLEncoder.encode(subscriptionId, StandardCharsets.UTF_8);
        return invokeService(url, "GET", null, token);
    }
}
