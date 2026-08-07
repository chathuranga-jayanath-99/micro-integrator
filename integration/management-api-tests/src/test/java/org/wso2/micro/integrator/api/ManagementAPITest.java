package org.wso2.micro.integrator.api;

import org.apache.http.HttpResponse;
import org.awaitility.Awaitility;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeSuite;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class ManagementAPITest extends ESBIntegrationTest {

    protected static final String LIST = "list";
    protected static final String COUNT = "count";
    protected static String accessToken;
    private static String endpoint;

    @BeforeSuite
    public void setEnvironment() throws Exception {
        super.init();
        accessToken = TokenUtil.getAccessToken(hostName, portOffset);
        endpoint = "https://" + hostName + ":" + (DEFAULT_INTERNAL_API_HTTPS_PORT + portOffset) + "/management/";
    }

    @BeforeClass
    protected void init() throws Exception {
        super.init();
    }

    protected JSONObject sendHttpRequestAndGetPayload(String resourcePath) throws IOException {
        waitForManagementApi();
        Assert.assertNotNull(accessToken);
        SimpleHttpClient client = new SimpleHttpClient();
        HttpResponse response = client.doGet(getManagementEndpoint(resourcePath), getHeaderMap());
        String responsePayload = client.getResponsePayload(response);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200);
        JSONObject jsonResponse = new JSONObject(responsePayload);
        return jsonResponse;
    }

    protected void waitForManagementApi() {
        if (!isManagementApiAvailable) {
            Awaitility.await().pollInterval(100, TimeUnit.MILLISECONDS).atMost(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                    .until(isManagementApiAvailable());
        }
    }

    protected String getManagementEndpoint(String resource) {
        return endpoint.concat(resource);
    }

    protected Map<String, String> getHeaderMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer ".concat(accessToken));
        return headers;
    }

    protected void verifyResourceCount(JSONObject jsonResponse, int expectedCount) {
        Assert.assertEquals(jsonResponse.get(COUNT), expectedCount, "Assert Failed due to the mismatch of " +
                "actual vs expected resource count");
    }

    protected void verifyResourceInfo(JSONObject jsonResponse, String[] expectedResourceNames ) {
        for (String expectedResourceName : expectedResourceNames) {
            Assert.assertTrue(jsonResponse.get(LIST).toString().contains(expectedResourceName), "Assert failed " +
                    "since expected resource name not found in the list");
        }
    }

    protected String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding is not supported", e);
        }
    }

}
