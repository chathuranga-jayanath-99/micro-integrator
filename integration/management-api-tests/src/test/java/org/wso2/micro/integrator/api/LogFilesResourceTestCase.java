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
import java.util.Map;

public class LogFilesResourceTestCase extends ManagementAPITest {

    private static String resourcePath = "logs";
    private static final String EXISTING_LOG_FILE_NAME = "wso2error.log";

    @Test(groups = {"wso2.esb"}, description = "Test get Logfiles resource")
    public void retrieveLogs() throws IOException {
        JSONObject jsonResponse = sendHttpRequestAndGetPayload(resourcePath);
        verifyResourceCount(jsonResponse, 9);
        verifyResourceInfo(jsonResponse, new String[]{"wso2error.log", "wso2-mi-service.log"});
    }

    @Test(groups = {"wso2.esb"}, description = "Test get log file resource for search key")
    public void retrieveSearchedLogs() throws IOException {
        JSONObject jsonResponse = sendHttpRequestAndGetPayload(resourcePath.concat("?searchKey=error"));
        verifyResourceCount(jsonResponse, 1);
        verifyResourceInfo(jsonResponse, new String[]{"wso2error.log"});
    }

    @Test(groups = {"wso2.esb"}, description = "Test downloading an existing log file succeeds")
    public void downloadExistingLogFile() throws IOException {
        waitForManagementApi();
        HttpResponse response = getLogFile(EXISTING_LOG_FILE_NAME);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), 200,
                "Expected a successful download for an existing log file");
    }

    @Test(groups = {"wso2.esb"}, description = "Test downloading a log file with a path traversal name is rejected")
    public void downloadLogFilePathTraversalRejected() throws IOException {
        waitForManagementApi();
        String[] maliciousNames = new String[] {
                "../../../../conf/deployment.toml",
                "..\\..\\..\\..\\conf\\deployment.toml"
        };
        for (String maliciousName : maliciousNames) {
            HttpResponse response = getLogFile(maliciousName);
            Assert.assertNotEquals(response.getStatusLine().getStatusCode(), 200,
                    "Path traversal attempt should not return the requested file : " + maliciousName);
        }
    }

    private HttpResponse getLogFile(String logFileName) throws IOException {
        String endpoint = getManagementEndpoint(resourcePath.concat("?file=").concat(urlEncode(logFileName)));
        Map<String, String> headers = getHeaderMap();
        SimpleHttpClient client = new SimpleHttpClient();
        return client.doGet(endpoint, headers);
    }

    @AfterClass(alwaysRun = true)
    public void cleanState() throws Exception {
        super.cleanup();
    }
}
