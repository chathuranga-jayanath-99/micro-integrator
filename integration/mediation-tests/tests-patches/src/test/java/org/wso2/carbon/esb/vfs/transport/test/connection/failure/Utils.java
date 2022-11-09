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

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Utils {

    private static Log log = LogFactory.getLog(Utils.class);
    private static final String SMB2_ROOT = "PATH_TO_SMB2_ROOT";
    private static final String SMB2_PASSWORD = "SMB2_PASSWORD";
    private static final String PASSWORD = "PASSWORD";
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
     * Method to return password from env variables
     */
    public static String getPassword() {
        return System.getenv(PASSWORD);
    }

    /**
     * Method to return smb2 password from env variables
     */
    public static String getSMB2User() {
        return System.getenv(SMB2_USER);
    }

    /**
     * Method to stop samba server and return status
     */
    public static void stopSambaServer() throws Exception {

        ProcessBuilder processBuilder = new ProcessBuilder();
        // Run a shell command
        processBuilder.command("/bin/bash", "-c", "echo " + getPassword() + "| sudo -S systemctl stop smbd" +
                ".service");
        try {
            Process process = processBuilder.start();
            int exitVal = process.waitFor();
            if (exitVal == 0) {
                log.info("Successfully stopped the Samba Server");
            } else {
                throw new Exception("Stopping SAMBA Server Failed");
            }

        } catch (IOException | InterruptedException e) {
            throw new Exception("Stopping SAMBA Server Failed", e);
        }
    }

    /**
     * Method to order smbd to close the client connections
     */
    public static void closeConnectionsToSambaServer() throws Exception {

        ProcessBuilder processBuilder = new ProcessBuilder();
        // Order smbd to close the client connections to the named share
        processBuilder.command("/bin/bash", "-c", "echo " + getPassword() + "| sudo -S smbcontrol smbd close-share share");
        try {
            Process process = processBuilder.start();
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            throw new Exception("Interrupting SAMBA Server Failed", e);
        }
    }

    /**
     * Method to start samba server
     */
    public static void startSambaServer() throws Exception {

        ProcessBuilder processBuilder = new ProcessBuilder();
        // Run a shell command
        processBuilder.command("/bin/bash", "-c", "echo " + getPassword() + "| sudo -S systemctl start smbd" +
                ".service");
        try {
            Process process = processBuilder.start();
            int exitVal = process.waitFor();
            if (exitVal == 0) {
                log.info("Successfully Started the Samba Server");
            } else {
                throw new Exception("Starting SAMBA Server Failed");
            }

        } catch (IOException | InterruptedException e) {
            throw new Exception("Starting SAMBA Server Failed", e);
        }
    }

    /**
     * Method to get status of samba server
     */
    public static boolean getStatusSambaServer() throws Exception {

        ProcessBuilder processBuilder = new ProcessBuilder();
        // Run a shell command
        processBuilder.command("/bin/bash", "-c", "systemctl status smbd.service");

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }

            int exitVal = process.waitFor();
            if (exitVal == 0 || exitVal == 3) {
                log.info("Successfully get the the Samba Server Status: " + output);
                if (output.toString().contains("(running)")) {
                    return true;
                } else {
                    return false;
                }
            } else {
                throw new Exception("Getting SAMBA Server Status failed");
            }

        } catch (IOException | InterruptedException e) {
            throw new Exception("Getting SAMBA Server Status", e);
        }
    }

    /*
     * Get number of files in a folder
     * */
    public static int getFileCount(File folder) {
        File[] files = folder.listFiles();
        return files.length;
    }

    public static void deleteDirectory(File directory) throws IOException {
        FileUtils.deleteDirectory(directory);
    }
}