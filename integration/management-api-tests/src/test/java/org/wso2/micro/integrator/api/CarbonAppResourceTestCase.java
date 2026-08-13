/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.api;

import org.apache.http.HttpResponse;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.clients.SimpleHttpClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;


public class CarbonAppResourceTestCase extends ManagementAPITest {

    private static String resourcePath = "applications";
    private final static String ACTIVE_LIST = "activeList";
    private final static String FAULTY_LIST = "faultyList";
    private final static String TOTAL_COUNT = "totalCount";
    private static final String EXISTING_CAPP_FILE_NAME = "hello-worldCompositeExporter_1.0.0.car";

    @Test(groups = { "wso2.esb" }, description = "Test get carbon applications resource")
    public void retrieveCApps() throws IOException {
        JSONObject jsonResponse = sendHttpRequestAndGetPayload(resourcePath);
        verifyTotalResourceCount(jsonResponse, 4);
        verifyFaultyResourceInfo(jsonResponse, new String[]{"FaultyCAppCompositeExporter"});
        verifyActiveResourceInfo(jsonResponse, new String[]{"hello-worldCompositeExporter"});
    }

    @Test(groups = { "wso2.esb" }, description = "Test get carbon applications resource for search key")
    public void retrieveSearchedCApps() throws IOException {
        JSONObject jsonResponse = sendHttpRequestAndGetPayload(resourcePath.concat("?searchKey=FaultyCApp"));
        verifyTotalResourceCount(jsonResponse, 1);
        verifyFaultyResourceInfo(jsonResponse, new String[]{"FaultyCAppCompositeExporter"});
    }

    @Test(groups = { "wso2.esb" }, description = "Test downloading an existing carbon application succeeds")
    public void downloadExistingCApp() throws IOException {
        waitForManagementApi();
        HttpResponse response = getCAppFile(EXISTING_CAPP_FILE_NAME);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                "Expected a successful download for an existing carbon application");
    }

    @Test(groups = { "wso2.esb" }, description = "Test downloading a carbon application with a path traversal " +
            "name is rejected")
    public void downloadCAppPathTraversalRejected() throws IOException {
        waitForManagementApi();
        String[] maliciousNames = new String[] {
                "../../../../../../etc/passwd",
                "..\\..\\..\\..\\repository\\conf\\deployment.toml"
        };
        for (String maliciousName : maliciousNames) {
            HttpResponse response = getCAppFile(maliciousName);
            Assert.assertNotEquals(response.getStatusLine().getStatusCode(), 200,
                    "Path traversal attempt should not return the requested file : " + maliciousName);
        }
    }

    @Test(groups = { "wso2.esb" }, description = "Test uploading a carbon application with a path traversal " +
            "file name is rejected")
    public void uploadCAppPathTraversalRejected() throws IOException {
        waitForManagementApi();
        String endpoint = getManagementEndpoint(resourcePath);
        SimpleHttpClient client = new SimpleHttpClient();
        String[] maliciousNames = new String[] {
                "../../../../../../tmp/evil.car",
                "..\\..\\..\\..\\evil.car"
        };
        for (String maliciousName : maliciousNames) {
            HttpResponse response = client.doPostWithMultipart(endpoint, maliciousName,
                    "malicious-content".getBytes(StandardCharsets.UTF_8), getHeaderMap());
            String responsePayload = client.getResponsePayload(response);
            Assert.assertEquals(response.getStatusLine().getStatusCode(), 400,
                    "Expected the upload to be rejected for file name : " + maliciousName + " but got response : "
                            + responsePayload);
        }
    }

    private HttpResponse getCAppFile(String cAppName) throws IOException {
        String endpoint = getManagementEndpoint(resourcePath.concat("?carbonAppName=").concat(urlEncode(cAppName)));
        Map<String, String> headers = getHeaderMap();
        headers.put("Accept", "application/octet-stream");
        SimpleHttpClient client = new SimpleHttpClient();
        return client.doGet(endpoint, headers);
    }

    @AfterClass(alwaysRun = true)
    public void cleanState() throws Exception {
        super.cleanup();
    }

    protected void verifyTotalResourceCount(JSONObject jsonResponse, int expectedCount) {
        Assert.assertEquals(jsonResponse.get(TOTAL_COUNT), expectedCount, "Assert Failed due to the mismatch of " +
                "actual vs expected resource count");
    }

    protected void verifyActiveResourceInfo(JSONObject jsonResponse, String[] expectedResourceNames) {
        for (String expectedResourceName : expectedResourceNames) {
            Assert.assertTrue(jsonResponse.get(ACTIVE_LIST).toString().contains(expectedResourceName),
                    "Assert failed since expected resource name not found in the list");
        }
    }

    protected void verifyFaultyResourceInfo(JSONObject jsonResponse, String[] expectedResourceNames) {
        for (String expectedResourceName : expectedResourceNames) {
            Assert.assertTrue(jsonResponse.get(FAULTY_LIST).toString().contains(expectedResourceName),
                    "Assert failed since expected resource name not found in the list");
        }
    }
}
