/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.deployment.application.deployer;

import org.apache.axis2.deployment.repository.util.DeploymentFileData;
import org.junit.After;
import org.junit.Test;
import org.wso2.config.mapper.ConfigParser;
import org.wso2.micro.application.deployer.CarbonApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link CappDeployer}.
 *
 * <h3>Sort / priority ordering ({@link CappDeployer#sort})</h3>
 * <p>A CApp is classified as <b>high priority</b> if it contains any artifact with one of:
 * <ul>
 *   <li>{@code lib/synapse/mediator} – class mediator</li>
 *   <li>{@code synapse/lib}          – connector</li>
 *   <li>{@code registry/resource}    – registry resource</li>
 * </ul>
 * All other CApps are classified as <b>low priority</b>. After sorting, high-priority CApps
 * appear first (alphabetically), followed by low-priority CApps (also alphabetically).
 */
public class CappDeployerTest {

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String PRIORITY_CONFIG_KEY = "carbon_apps.enable_priority_deployment";

    /** Tracks all temporary .car files created during a test so they can be cleaned up. */
    private final List<File> tempFiles = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (File file : tempFiles) {
            if (file.exists()) {
                file.delete();
            }
        }
        tempFiles.clear();
        // Reset static state that the retry tests may have modified.
        setStaticField("faultyCapps", new ArrayList<>());
        setStaticField("faultyCAppObjects", new ArrayList<>());
        setStaticField("cAppMap", new ArrayList<>());
        // Remove the priority deployment config key so each test starts from a clean slate.
        ConfigParser.getParsedConfigs().remove(PRIORITY_CONFIG_KEY);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal .car (zip) file containing one artifact directory per supplied type.
     * Each artifact directory contains a single {@code artifact.xml} that sets the given type.
     *
     * @param fileName      name of the .car file to create (e.g. "my-app.car")
     * @param artifactTypes one or more artifact type strings to embed in the archive
     * @return the created temporary {@link File}
     */
    private File createCarFile(String fileName, String... artifactTypes) throws IOException {
        File carFile = new File(TEMP_DIR, fileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(carFile))) {
            for (int i = 0; i < artifactTypes.length; i++) {
                // Directory entry (artifact dir)
                String dirEntry = "artifact-" + i + "_1.0.0/";
                zos.putNextEntry(new ZipEntry(dirEntry));
                zos.closeEntry();
                // artifact.xml entry inside the directory
                zos.putNextEntry(new ZipEntry(dirEntry + "artifact.xml"));
                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<artifact name=\"artifact-" + i + "\" version=\"1.0.0\""
                        + " type=\"" + artifactTypes[i] + "\""
                        + " serverRole=\"EnterpriseIntegrator\">\n"
                        + "    <file>artifact-" + i + ".zip</file>\n"
                        + "</artifact>";
                zos.write(xml.getBytes("UTF-8"));
                zos.closeEntry();
            }
        }
        tempFiles.add(carFile);
        return carFile;
    }

    /** Wraps a {@link File} in a {@link DeploymentFileData} instance. */
    private DeploymentFileData toFileData(File file) {
        return new DeploymentFileData(file);
    }

    /** Creates a {@link CappDeployer} with the shared temp directory set as the CApp directory. */
    private CappDeployer createDeployer() {
        CappDeployer deployer = new CappDeployer();
        deployer.setDirectory(TEMP_DIR);
        return deployer;
    }

    /** Extracts just the file name from a {@link DeploymentFileData} for assertion readability. */
    private String nameOf(DeploymentFileData fileData) {
        return new File(fileData.getAbsolutePath()).getName();
    }

    /** Injects the priority deployment flag into ConfigParser so sort() takes the priority path. */
    private static void enablePriorityDeployment() {
        ConfigParser.getParsedConfigs().put(PRIORITY_CONFIG_KEY, true);
    }

    // -------------------------------------------------------------------------
    // Tests: priority classification by artifact type
    // -------------------------------------------------------------------------

    /**
     * A CApp containing a connector artifact ({@code synapse/lib}) must be classified as
     * high priority and placed before a low-priority CApp when sorted.
     */
    @Test
    public void testConnectorArtifactTypeIsHighPriority() throws IOException {
        enablePriorityDeployment();
        File connector = createCarFile("connector-app.car", "synapse/lib");
        File regular   = createCarFile("regular-app.car",   "synapse/api");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(regular));
        files.add(toFileData(connector));

        createDeployer().sort(files, 0, 2);

        assertEquals("connector-app.car should be first (high priority)", "connector-app.car", nameOf(files.get(0)));
        assertEquals("regular-app.car should be second (low priority)",   "regular-app.car",   nameOf(files.get(1)));
    }

    /**
     * A CApp containing a class mediator artifact ({@code lib/synapse/mediator}) must be
     * classified as high priority and placed before a low-priority CApp when sorted.
     */
    @Test
    public void testClassMediatorArtifactTypeIsHighPriority() throws IOException {
        enablePriorityDeployment();
        File classMediator = createCarFile("class-mediator-app.car", "lib/synapse/mediator");
        File regular       = createCarFile("regular-app.car",        "synapse/proxy-service");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(regular));
        files.add(toFileData(classMediator));

        createDeployer().sort(files, 0, 2);

        assertEquals("class-mediator-app.car should be first (high priority)", "class-mediator-app.car", nameOf(files.get(0)));
        assertEquals("regular-app.car should be second (low priority)",         "regular-app.car",         nameOf(files.get(1)));
    }

    /**
     * A CApp containing a registry resource artifact ({@code registry/resource}) must be
     * classified as high priority and placed before a low-priority CApp when sorted.
     */
    @Test
    public void testRegistryResourceArtifactTypeIsHighPriority() throws IOException {
        enablePriorityDeployment();
        File registry = createCarFile("registry-app.car", "registry/resource");
        File regular  = createCarFile("regular-app.car",  "synapse/sequence");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(regular));
        files.add(toFileData(registry));

        createDeployer().sort(files, 0, 2);

        assertEquals("registry-app.car should be first (high priority)", "registry-app.car", nameOf(files.get(0)));
        assertEquals("regular-app.car should be second (low priority)",  "regular-app.car",  nameOf(files.get(1)));
    }

    /**
     * A CApp that contains both a high-priority artifact type and a regular artifact type
     * must still be classified as high priority (any match is sufficient).
     */
    @Test
    public void testCAppWithMixedArtifactTypesIsHighPriority() throws IOException {
        enablePriorityDeployment();
        // This CApp has two artifacts: one connector (high priority) and one API (low priority)
        File mixed   = createCarFile("mixed-app.car",   "synapse/lib", "synapse/api");
        File regular = createCarFile("regular-app.car", "synapse/api");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(regular));
        files.add(toFileData(mixed));

        createDeployer().sort(files, 0, 2);

        assertEquals("mixed-app.car should be first (has a high-priority artifact)", "mixed-app.car", nameOf(files.get(0)));
        assertEquals("regular-app.car should be second (low priority)",              "regular-app.car", nameOf(files.get(1)));
    }

    // -------------------------------------------------------------------------
    // Tests: alphabetical ordering within each priority group
    // -------------------------------------------------------------------------

    /**
     * When all CApps are high priority, they must be sorted alphabetically (case-insensitive).
     */
    @Test
    public void testOnlyHighPriorityCAppsAreSortedAlphabetically() throws IOException {
        enablePriorityDeployment();
        File charlie = createCarFile("charlie-connector.car", "synapse/lib");
        File alpha   = createCarFile("alpha-registry.car",   "registry/resource");
        File bravo   = createCarFile("bravo-mediator.car",   "lib/synapse/mediator");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(charlie));
        files.add(toFileData(alpha));
        files.add(toFileData(bravo));

        createDeployer().sort(files, 0, 3);

        assertEquals("alpha-registry.car",   nameOf(files.get(0)));
        assertEquals("bravo-mediator.car",   nameOf(files.get(1)));
        assertEquals("charlie-connector.car", nameOf(files.get(2)));
    }

    /**
     * When all CApps are low priority, they must be sorted alphabetically (case-insensitive).
     */
    @Test
    public void testOnlyLowPriorityCAppsAreSortedAlphabetically() throws IOException {
        enablePriorityDeployment();
        File charlie = createCarFile("charlie-api.car", "synapse/api");
        File alpha   = createCarFile("alpha-seq.car",   "synapse/sequence");
        File bravo   = createCarFile("bravo-ep.car",    "synapse/endpoint");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(charlie));
        files.add(toFileData(alpha));
        files.add(toFileData(bravo));

        createDeployer().sort(files, 0, 3);

        assertEquals("alpha-seq.car",   nameOf(files.get(0)));
        assertEquals("bravo-ep.car",    nameOf(files.get(1)));
        assertEquals("charlie-api.car", nameOf(files.get(2)));
    }

    // -------------------------------------------------------------------------
    // Tests: combined high + low priority ordering
    // -------------------------------------------------------------------------

    /**
     * When a list contains both high- and low-priority CApps, the final order must be:
     * high-priority CApps sorted alphabetically, followed by low-priority CApps sorted
     * alphabetically.
     */
    @Test
    public void testHighPriorityCAppsAreDeployedBeforeLowPriorityCApps() throws IOException {
        enablePriorityDeployment();
        // Low priority
        File lowAlpha   = createCarFile("alpha-api.car",     "synapse/api");
        File lowCharlie = createCarFile("charlie-proxy.car", "synapse/proxy-service");
        // High priority
        File highBravo  = createCarFile("bravo-registry.car",  "registry/resource");
        File highDelta  = createCarFile("delta-connector.car", "synapse/lib");

        List<DeploymentFileData> files = new ArrayList<>();
        // Intentionally unordered
        files.add(toFileData(lowCharlie));    // 0
        files.add(toFileData(highDelta));     // 1
        files.add(toFileData(lowAlpha));      // 2
        files.add(toFileData(highBravo));     // 3

        createDeployer().sort(files, 0, 4);

        // High-priority group (alphabetical): bravo-registry, delta-connector
        assertEquals("bravo-registry.car",  nameOf(files.get(0)));
        assertEquals("delta-connector.car", nameOf(files.get(1)));
        // Low-priority group (alphabetical): alpha-api, charlie-proxy
        assertEquals("alpha-api.car",     nameOf(files.get(2)));
        assertEquals("charlie-proxy.car", nameOf(files.get(3)));
    }

    // -------------------------------------------------------------------------
    // Tests: sub-range boundary behaviour
    // -------------------------------------------------------------------------

    /**
     * The sort must only reorder elements within [startIndex, toIndex). Elements outside
     * that range must remain at their original positions.
     */
    @Test
    public void testSortOnlyAffectsElementsWithinSpecifiedSubRange() throws IOException {
        enablePriorityDeployment();
        File outsideBefore = createCarFile("z-outside-before.car", "synapse/api");
        File lowB          = createCarFile("bravo-low.car",        "synapse/api");
        File highA         = createCarFile("alpha-connector.car",  "synapse/lib");
        File outsideAfter  = createCarFile("z-outside-after.car",  "synapse/api");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(outsideBefore)); // index 0 – outside range
        files.add(toFileData(lowB));          // index 1 – inside range [1, 3)
        files.add(toFileData(highA));         // index 2 – inside range [1, 3)
        files.add(toFileData(outsideAfter));  // index 3 – outside range

        createDeployer().sort(files, 1, 3);

        // Sentinel elements outside the range are untouched
        assertEquals("z-outside-before.car", nameOf(files.get(0)));
        assertEquals("z-outside-after.car",  nameOf(files.get(3)));

        // Inside the range: high priority (alpha-connector) first, then low priority (bravo-low)
        assertEquals("alpha-connector.car", nameOf(files.get(1)));
        assertEquals("bravo-low.car",       nameOf(files.get(2)));
    }

    /**
     * When startIndex equals toIndex (an empty sub-range) the list must not be modified.
     */
    @Test
    public void testSortWithEmptySubRangeIsNoOp() throws IOException {
        enablePriorityDeployment();
        File fileA = createCarFile("a-connector.car", "synapse/lib");
        File fileB = createCarFile("b-regular.car",   "synapse/api");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(fileB));
        files.add(toFileData(fileA));

        createDeployer().sort(files, 1, 1);

        // Order must be unchanged
        assertEquals("b-regular.car",   nameOf(files.get(0)));
        assertEquals("a-connector.car", nameOf(files.get(1)));
    }

    // -------------------------------------------------------------------------
    // Tests: resilience / edge cases
    // -------------------------------------------------------------------------

    /**
     * A non-existent .car file cannot be opened as a zip archive. The deployer must treat
     * it as low priority (warn and continue) rather than throwing an exception.
     */
    @Test
    public void testNonExistentCarFileTreatedAsLowPriority() throws IOException {
        enablePriorityDeployment();
        // This file is never created on disk; it should be treated as low priority.
        File missing   = new File(TEMP_DIR, "missing-app.car");
        File connector = createCarFile("connector-app.car", "synapse/lib");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(missing));
        files.add(toFileData(connector));

        createDeployer().sort(files, 0, 2);

        // connector (high priority) should come first; missing file falls to low priority
        assertEquals("connector-app.car", nameOf(files.get(0)));
        assertEquals("missing-app.car",   nameOf(files.get(1)));
    }

    /**
     * A .car file that contains no artifact.xml entries must be treated as low priority.
     */
    @Test
    public void testCarFileWithNoArtifactXmlTreatedAsLowPriority() throws IOException {
        enablePriorityDeployment();
        // Create an empty .car archive (no entries)
        File empty     = new File(TEMP_DIR, "empty-app.car");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(empty))) {
            // intentionally left empty
        }
        tempFiles.add(empty);

        File connector = createCarFile("connector-app.car", "synapse/lib");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(empty));
        files.add(toFileData(connector));

        createDeployer().sort(files, 0, 2);

        assertEquals("connector-app.car", nameOf(files.get(0)));
        assertEquals("empty-app.car",     nameOf(files.get(1)));
    }

    /**
     * A single-element sub-range must remain unchanged regardless of the artifact type.
     */
    @Test
    public void testSortSingleElementSubRangeIsNoOp() throws IOException {
        enablePriorityDeployment();
        File connector = createCarFile("connector-app.car", "synapse/lib");
        File regular   = createCarFile("regular-app.car",   "synapse/api");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(regular));
        files.add(toFileData(connector));

        createDeployer().sort(files, 0, 1);

        // Only index 0 is in range; no reordering possible
        assertEquals("regular-app.car",   nameOf(files.get(0)));
        assertEquals("connector-app.car", nameOf(files.get(1)));
    }

    /**
     * When {@code carbon_apps.enable_priority_deployment} is absent from deployment.toml,
     * sort() must delegate to the superclass and must NOT apply priority hoisting.
     * A connector CApp placed after a regular CApp must stay after it because no
     * priority classification is performed.
     */
    @Test
    public void testSortDelegatesToSuperWhenPriorityDeploymentConfigIsDisabled() throws IOException {
        // Config key is deliberately absent (not calling enablePriorityDeployment()).
        // z-connector has a high-priority artifact type, but priority sort is off.
        // With priority disabled, z-connector.car must NOT be hoisted ahead of a-low-app.car.
        File lowAlpha      = createCarFile("a-low-app.car",    "synapse/api");
        File highZConnector = createCarFile("z-connector.car", "synapse/lib");

        List<DeploymentFileData> files = new ArrayList<>();
        files.add(toFileData(lowAlpha));        // index 0
        files.add(toFileData(highZConnector));  // index 1

        createDeployer().sort(files, 0, 2);

        // Priority sort would have moved z-connector.car (high priority) to position 0.
        // Without the config, the superclass handles ordering and no hoisting must occur.
        assertEquals("a-low-app.car should remain at position 0 — no priority hoisting",
                     "a-low-app.car", nameOf(files.get(0)));
        assertEquals("z-connector.car should remain at position 1 — no priority hoisting",
                     "z-connector.car", nameOf(files.get(1)));
    }

    // -------------------------------------------------------------------------
    // Helpers: reflection utilities for retryFaultyCApps tests
    // -------------------------------------------------------------------------

    private static void setStaticField(String fieldName, Object value) throws Exception {
        Field field = CappDeployer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void invokeRetryFaultyCApps(CappDeployer deployer) throws Exception {
        Method method = CappDeployer.class.getDeclaredMethod("retryFaultyCApps");
        method.setAccessible(true);
        method.invoke(deployer);
    }

    // -------------------------------------------------------------------------
    // Tests: retryFaultyCApps behaviour
    // -------------------------------------------------------------------------

    /**
     * retryFaultyCApps() must snapshot and clear both faultyCapps and faultyCAppObjects
     * before attempting any retry deployments, so that a CApp that succeeds on retry
     * lands in cAppMap with a clean slate rather than remaining in the faulty lists.
     * <p>
     * Individual deploy attempts during the retry fail silently (invalid files, no
     * axisConfig), so both lists stay empty after the call.
     */
    @Test
    public void testRetryFaultyCappsClearsBothListsBeforeRetry() throws Exception {
        ArrayList<String> faulty = new ArrayList<>(Arrays.asList("app-a.car", "app-b.car"));
        ArrayList<CarbonApplication> faultyObjects = new ArrayList<>();
        faultyObjects.add(null); // as happens in prod when currentApp is null on failure
        setStaticField("faultyCapps", faulty);
        setStaticField("faultyCAppObjects", faultyObjects);

        invokeRetryFaultyCApps(createDeployer());

        assertTrue("faultyCapps must be cleared before retry attempts",
                   CappDeployer.getFaultyCapps().isEmpty());
        assertTrue("faultyCAppObjects must be cleared before retry attempts",
                   CappDeployer.getFaultyCAppObjects().isEmpty());
    }

    /**
     * When there are no faulty CApps, retryFaultyCApps() must complete without
     * throwing an exception and both faulty lists must remain empty.
     */
    @Test
    public void testRetryFaultyCAppsIsNoOpWhenFaultyListIsEmpty() throws Exception {
        invokeRetryFaultyCApps(createDeployer()); // must not throw

        assertTrue("faultyCapps must remain empty", CappDeployer.getFaultyCapps().isEmpty());
        assertTrue("faultyCAppObjects must remain empty", CappDeployer.getFaultyCAppObjects().isEmpty());
    }

    /**
     * A newly created CappDeployer must have retryPassCompleted as false, meaning
     * the one-shot retry pass is eligible to run during the first startup cycle.
     */
    @Test
    public void testRetryPassCompletedIsFalseForNewDeployer() throws Exception {
        Field field = CappDeployer.class.getDeclaredField("retryPassCompleted");
        field.setAccessible(true);
        assertFalse("retryPassCompleted must be false on a fresh deployer instance",
                    (Boolean) field.get(createDeployer()));
    }
}
