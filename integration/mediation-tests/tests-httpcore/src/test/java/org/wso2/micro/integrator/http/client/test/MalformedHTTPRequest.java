/*
 * Copyright (c) 2022, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.micro.integrator.http.client.test;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.esb.integration.common.utils.clients.tcpclient.Client;

import java.io.PrintWriter;

import static org.wso2.micro.integrator.http.client.test.Constants.API_CONTEXT;
import static org.wso2.micro.integrator.http.client.test.Constants.CRLF;
import static org.wso2.micro.integrator.http.client.test.Utils.getTCPClient;

/**
 * Test case tests for MI behaviour(specifically CPU usage) when a Malformed HTTPS request is received.
 * In this case, We are sending an invalid header, ie not as a key : value pair
 */
public class MalformedHTTPRequest extends HTTPCoreClientTest {

    @Test(groups = {"wso2.esb"}, description = "Test for MI behaviour when a Malformed HTTPS request is received.",
            dataProvider = "httpRequests", dataProviderClass = Constants.class)
    public void testMalformedHTTPSRequest(HTTPRequest httpRequest) throws Exception {

        Client tcpClient = getTCPClient(httpRequest);
        tcpClient.open();
        sendMalformedHTTPRequest(tcpClient.getPrintWriter(), httpRequest.getMethod());

        assertCPUUsageBeforeClosingSocket();

        Assert.assertTrue(tcpClient.getResponseAsString().contains("400 Bad request"), "Expected a 400 Bad request");

        tcpClient.close();

        assertCPUUsageAfterClosingSocket();
    }

    private static void sendMalformedHTTPRequest(PrintWriter printWriter, RequestMethods method) {

        printWriter.print(method + " " + API_CONTEXT + " HTTP/1.1" + CRLF);
        // sending an invalid header
        printWriter.print(
                "odHRwczovL2VpZHAtdWF0LmRlLmRiLmNvbS9hdXRoL3JlYWxtcy9nbG9iYWwiLCJhdWQiOiIxMDEyMzUtMV9CYW5rQVBJLWRiQVBJIiwic3ViIjoiMTUwMmQzNWYtYWZmZi00YzNmLTgxMzQtODY0MGVhNzQyZDljIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiMTAxMjM1LTFfQmFua0FQSS1kYkFQSSIsInNlc3Npb25fc3RhdGUiOiIxYjUzNzNiYS02ZDMwLTQzZWMtYWVjNy0xY2Q0MGI1NjEzNmYiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzdmNfYmFua2FwaSIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiXX0sInNpZCI6IjFiNTM3M2JhLTZkMzAtNDNlYy1hZWM3LTFjZDQwYjU2MTM2ZiIsIm5hcmlkIjoiMTAxMjM1LTEiLCJzY3AiOlsibGc6Q0EvQUJGIiwibGc6WlZLL1NBTERPIiwibGc6S0svVU1TIiwibGc6QkZFL1BFUkYiLCJsZzpCRkUvVUVCIiwibGc6QkZFL1VNUyIsImxnOkJGRS9VTVMiLCJsZzpDQk8vQU5aRVAiLCJsZzpDQk8vQU5aRVAiLCJsZzpQQTcvUFJPRFUiLCJsZzpQQTcvS1lDSUYiXSwibGVnaXRpbWF0aW9ucyI6W3siaWQiOiJCRDEwMDgiLCJndm8iOlsiQ0EvQUJGIiwiWlZLL1NBTERPIiwiS0svVU1TIiwiQkZFL1BFUkYiLCJCRkUvVUVCIiwiQkZFL1VNUyIsIkJGRS9VTVMiLCJDQk8vQU5aRVAiLCJDQk8vQU5aRVAiLCJQQTcvUFJPRFUiLCJQQTcvS1lDSUYiXSwibGdfbmFyaWQiOiIxMDEyMzUtMSIsImxlZ2lfYXV0aCI6WyJsZzpDQS9BQkYiLCJsZzpaVksvU0FMRE8iLCJsZzpLSy9VTVMiLCJsZzpCRkUvUEVSRiIsImxnOkJGRS9VRUIiLCJsZzpCRkUvVU1TIiwibGc6QkZFL1VNUyIsImxnOkNCTy9BTlpFUCIsImxnOkNCTy9BTlpFUCIsImxnOlBBNy9QUk9EVSIsImxnOlBBNy9LWUNJRiJdLCJpYXQiOjE2NTYwMDg4NDJ9XSwiZGJsZWdpaWQiOiJCRDEwMDgiLCJsYXN0X2F1dGgiOjE2MzQwNTgwNTl9.eY4UaHKVIjNxEqtW-PK146GMvPs4W91dCot7NutpriUmmQyL5E1R8LfcFAcLG3SoCBI99yNDHcRVoX6jB_Uy-k0Ua8uzbrF691SKA_b5_unUKDUmH01m-SvmTB3K0PsLAL4WbBHiPHFtar3iyT3sLRYuBob5H7meBloEBJIveJqtTHFs-y7EG4h6aNLv1BwlvCvz-halYN-ES7txxkj7UdAtHbahmPjqKKRW2t-_JbxgZKPtMSYS4d_pcBLCqeIVlVZdN44FpQ6EpjaEKFGGcEfiUn05qk6-cLWs9muYw5Jf_ChsXn9btv-ihQaEQlSog2OTJ8ySaUKx3CrVHHIY9A\r\n");
        printWriter.print("Accept: application/json" + CRLF);
        printWriter.print("Connection: keep-alive" + CRLF);
        printWriter.print(CRLF);
        printWriter.flush();
        printWriter.print(CRLF);
        printWriter.flush();
    }
}
