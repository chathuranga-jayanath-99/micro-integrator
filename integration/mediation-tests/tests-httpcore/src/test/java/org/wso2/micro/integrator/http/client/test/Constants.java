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

import org.testng.annotations.DataProvider;
import org.wso2.carbon.utils.ServerConstants;

import java.io.File;

/**
 * This class contains the constants used in HTTP Core test cases.
 */
public class Constants {

    public static final String KEYSTORE_PATH =
            System.getProperty(ServerConstants.CARBON_HOME) + File.separator + "repository" + File.separator +
                    "resources" + File.separator +
                    "security" + File.separator + "wso2carbon.jks";

    public static final String KEYSTORE_PASS = "wso2carbon";
    public static final String HOST = "localhost";
    public static final int HTTP_PORT = 8480;
    public static final int HTTPS_PORT = 8443;
    public static final String API_CONTEXT = "/httpcore-test";
    public static final String CRLF = "\r\n";

    public static final int CPU_POLL_TIMEOUT = 30;
    public static final int CPU_POLL_INTERVAL = 5;

    public static final String HTTP_SC_200 = "200 OK";
    public static final String HTTP_SC_202 = "202 Accepted";
    public static final String HTTP_SC_400 = "400 Bad request";

    @DataProvider(name = "httpRequests")
    public static Object[][] httpRequests() {

        return new Object[][]{
                {new HTTPRequest(RequestMethods.GET, PayloadSize.EMPTY, true)},
                {new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, true)},
                {new HTTPRequest(RequestMethods.GET, PayloadSize.LARGE, true)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.EMPTY, true)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, true)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.LARGE, true)},
                {new HTTPRequest(RequestMethods.GET, PayloadSize.EMPTY, false)},
                {new HTTPRequest(RequestMethods.GET, PayloadSize.SMALL, false)},
                {new HTTPRequest(RequestMethods.GET, PayloadSize.LARGE, false)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.EMPTY, false)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.SMALL, false)},
                {new HTTPRequest(RequestMethods.POST, PayloadSize.LARGE, false)}
        };
    }
}
