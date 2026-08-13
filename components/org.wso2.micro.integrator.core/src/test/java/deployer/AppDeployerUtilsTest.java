/*
 *
 *  *Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *  *
 *  *WSO2 Inc. licenses this file to you under the Apache License,
 *  *Version 2.0 (the "License"); you may not use this file except
 *  *in compliance with the License.
 *  *You may obtain a copy of the License at
 *  *
 *  *http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *Unless required by applicable law or agreed to in writing,
 *  *software distributed under the License is distributed on an
 *  *"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  *KIND, either express or implied.  See the License for the
 *  *specific language governing permissions and limitations
 *  *under the License.
 *  
 */

package deployer;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wso2.micro.application.deployer.AppDeployerUtils;
import org.wso2.micro.core.util.CarbonException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AppDeployerUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void createRegistryPath_govRegistryPath() {

        final String inputPath = "/_system/governance/my-resource.xml";
        final String expectedOutput = "gov:/my-resource.xml";
        final String result = AppDeployerUtils.createRegistryPath(inputPath);
        Assert.assertEquals(expectedOutput, result);
    }

    @Test
    public void createRegistryPath_configRegistryPath() {

        final String inputPath = "/_system/config/my-resource.xml";
        final String expectedOutput = "conf:/my-resource.xml";
        final String result = AppDeployerUtils.createRegistryPath(inputPath);
        Assert.assertEquals(expectedOutput, result);
    }

    @Test(expected = CarbonException.class)
    public void extractCarbonApp_rejectsZipSlipEntry() throws Exception {

        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("../../evil.txt", "pwned");
        File maliciousCar = createZip(tempFolder.newFile("malicious.car"), entries);

        AppDeployerUtils.extractCarbonApp(maliciousCar.getAbsolutePath());
    }

    @Test
    public void extractCarbonApp_extractsNestedEntriesWithinDestination() throws Exception {

        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("a/", null);
        entries.put("a/b/", null);
        entries.put("a/b/file.xml", "<config/>");
        File goodCar = createZip(tempFolder.newFile("good.car"), entries);

        String dest = AppDeployerUtils.extractCarbonApp(goodCar.getAbsolutePath());

        File extractedFile = new File(dest, "a/b/file.xml");
        Assert.assertTrue("Expected nested entry to be extracted at : " + extractedFile, extractedFile.exists());
    }

    private File createZip(File target, LinkedHashMap<String, String> entries) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            for (java.util.Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return target;
    }
}