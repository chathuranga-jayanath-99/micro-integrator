/*
 *Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *WSO2 Inc. licenses this file to you under the Apache License,
 *Version 2.0 (the "License"); you may not use this file except
 *in compliance with the License.
 *You may obtain a copy of the License at
 *
 *http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing,
 *software distributed under the License is distributed on an
 *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *KIND, either express or implied.  See the License for the
 *specific language governing permissions and limitations
 *under the License.
 */
package org.wso2.carbon.esb.vfs.transport.test.connection.failure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Utils {

    private static Log log = LogFactory.getLog(Utils.class);
    private static final String SMB2_ROOT = "PATH_TO_SMB2_ROOT";
    private static final String SMB2_PASSWORD = "SMB2_PASSWORD";
    private static final String SMB2_USER = "SMB2_USER";

    /**
     * Method to return smb2 root from env variables
     */
    public static String getSMB2Root() {
        return System.getenv(SMB2_ROOT);
    }

    /**
     * Method to return smb2 password from env variables
     */
    public static String getSMB2Password() {
        return System.getenv(SMB2_PASSWORD);
    }

    /**
     * Method to return smb2 password from env variables
     */
    public static String getSMB2User() {
        return System.getenv(SMB2_USER);
    }

}