/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.carbon.esb.json;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class EmptyRequestPayloadWithJSONResponseTest extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        super.init();
    }

    @Test(groups = "wso2.esb", description = "Check whether JSON payload response comes as it is when" +
            " empty payload is sent as request")
    public void testEmptyRequestPayloadWithJSONResponse() throws Exception {

        Map<String, String> headers = new HashMap<>();
        // setting default content type as application/octet-stream
        headers.put("Content-Type", "application/octet-stream");
        String expectedResponse = "{\n" +
                "                    \"response\": {\n" +
                "                    \"status\": \"Success\",\n" +
                "                    \"message\": \"This is a sample response.\",\n" +
                "                    \"code\": \"200\"\n" +
                "                    }\n" +
                "                    }";
        HttpResponse response = HttpRequestUtil
                .doPost(new URL(getApiInvocationURL("testEmptyRequestPayloadWithJSONResponse")), "", headers);
        String responsePayload = response.getData();
        System.out.println(responsePayload);
        Assert.assertEquals(response.getData(), expectedResponse,
                "JSON payload response does not come as it is when empty payload is sent as request");
    }
}
