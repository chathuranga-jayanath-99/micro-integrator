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

package org.wso2.micro.integrator.http.backend.test;

import org.apache.http.HttpResponse;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.wso2.micro.integrator.http.backend.test.Constants.HTTP_VERSION;
import static org.wso2.micro.integrator.http.backend.test.Utils.getServerSocket;
import static org.wso2.micro.integrator.http.client.test.Constants.CRLF;
import static org.wso2.micro.integrator.http.client.test.Utils.getPayload;

public class BackendRespondWith500TestCase extends HTTPCoreBackendTest {

    @Test(groups = {"wso2.esb"}, description =
            "Test for MI behaviour when a backend sends a 500 Internal Server Error response.",
            dataProvider = "httpRequestResponse", dataProviderClass = Constants.class)
    public void testBackendRespondWith500(HTTPRequestWithBackendResponse httpRequestWithBackendResponse) throws Exception {

        HttpResponse response = invokeHTTPCoreBETestAPI(httpRequestWithBackendResponse);

        assertCPUUsage();

        assertEquals(response.getStatusLine().getStatusCode(), 500, "Response not received");

        assertEquals(client.getResponsePayload(response).getBytes().length,
                getPayload(httpRequestWithBackendResponse.getBackendResponse().getBackendPayloadSize())
                        .getBytes().length,
                "Response size mismatch");
    }

    @Override
    protected List<BackendServer> getBackEndServers() throws Exception {

        List<BackendServer> serverList = new ArrayList<>();
        serverList.add(new BackendServerResponseWith500(getServerSocket(true)));
        serverList.add(new BackendServerResponseWith500(getServerSocket(false)));

        return serverList;
    }

    private static class BackendServerResponseWith500 extends BackendServer {

        public BackendServerResponseWith500(ServerSocket serverSocket) {

            super(serverSocket);
        }

        @Override
        protected synchronized void writeOutput(Socket socket) throws Exception {

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            out.write(HTTP_VERSION + " 500 Internal Server Error" + CRLF);
            out.write("Content-Type: application/json" + CRLF);
            out.write("Content-Length:  " + payload.getBytes().length + CRLF);
            out.write("Connection: Close" + CRLF);
            out.write(CRLF);
            out.write(payload);
            out.flush();
            socket.close();
        }
    }
}
