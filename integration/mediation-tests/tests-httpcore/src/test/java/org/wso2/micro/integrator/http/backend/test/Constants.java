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

import org.testng.annotations.DataProvider;
import org.wso2.micro.integrator.http.client.test.HTTPRequest;
import org.wso2.micro.integrator.http.client.test.PayloadSize;
import org.wso2.micro.integrator.http.client.test.RequestMethods;

public class Constants {

    public static final int HTTP_BACKEND_PORT = 7000;
    public static final int HTTPS_BACKEND_PORT = 7443;
    public static final String HTTP_VERSION = "HTTP/1.1";
    public static final String API_CONTEXT = "httpcore-backend-test";

    @DataProvider(name = "httpRequestResponse")
    private static Object[][] httpRequestResponse() {

        return new Object[][]{
                // Backend response with Empty Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.EMPTY, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.EMPTY, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.EMPTY, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.EMPTY, false))},

                // Backend response with Small Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.SMALL, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.SMALL, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.SMALL, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.SMALL, false))},

                // Backend response with Large Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.LARGE, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.LARGE, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.LARGE, false))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.LARGE, false))},

                // SSL Backend response with Empty Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.EMPTY, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.EMPTY, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.EMPTY, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.EMPTY, true))},

                // SSL Backend response with Small Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.SMALL, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.SMALL, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.SMALL, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.SMALL, true))},

                // SSL Backend response with Large Payload
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.LARGE, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false),
                        new BackendResponse(PayloadSize.LARGE, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.LARGE, true))},
                {new HTTPRequestWithBackendResponse(new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true),
                        new BackendResponse(PayloadSize.LARGE, true))}
        };
    }
}
