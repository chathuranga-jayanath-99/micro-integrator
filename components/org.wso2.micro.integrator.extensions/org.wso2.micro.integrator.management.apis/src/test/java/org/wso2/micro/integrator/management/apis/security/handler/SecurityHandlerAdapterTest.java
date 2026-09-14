/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.management.apis.security.handler;

import org.apache.axis2.addressing.EndpointReference;
import org.apache.synapse.MessageContext;
import org.junit.Assert;
import org.junit.Test;
import org.wso2.micro.integrator.management.apis.Constants;
import java.util.ArrayList;
import java.util.List;

public class SecurityHandlerAdapterTest {

    /**
     * Tests a handler configuration similar to the following with no resources defined.
     * <p>
     * <handler name="SampleInternalApiHandlerWithNoResources"
     *          class="internal.http.api.SampleInternalApiHandlerWithNoResources"/>
     */
    @Test
    public void testHandledWithNoResource() {

        //set message context
        MessageContext messageContext = new TestMessageContext();
        EndpointReference endpointReference = new EndpointReference();
        endpointReference.setAddress("/sectest/resource1");
        messageContext.setTo(endpointReference);

        TestSecurityHandler internalAPIHandler = new TestSecurityHandler("/sectest");

        //test with no resources
        internalAPIHandler.setResources(new ArrayList<>());
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged when no resources are explictely defined, but it was not engaged.",
                          internalAPIHandler.isHandleTriggered());
    }

    /**
     * Tests a handler configuration similar to the following.
     * <p>
     * <handler name="SampleInternalApiHandlerWithCustomResources"
     *          class="internal.http.api.SampleInternalApiHandlerWithCustomResources">
     *  <resources>
     *      <resource>/resource1</resource>
     *      <resource>/resource2</resource>
     *  </resources>
     *</handler>
     */
    @Test
    public void testHandledWithCustomResources() {

        //Create test message context
        MessageContext messageContext = new TestMessageContext();
        EndpointReference endpointReference = new EndpointReference();
        messageContext.setTo(endpointReference);

        TestSecurityHandler internalAPIHandler = new TestSecurityHandler("/sectest");
        List<String> resources = new ArrayList<>();
        resources.add("/resource1");
        resources.add("/resource2");
        internalAPIHandler.setResources(resources);

        //set message context with matching resource
        endpointReference.setAddress("/sectest/resource1");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged since resource 1 was defined, but it was not engaged.",
                          internalAPIHandler.isHandleTriggered());

        //set message context with matching resource
        endpointReference.setAddress("/sectest/resource2");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged since resource 2 was defined, but it was not engaged.",
                          internalAPIHandler.isHandleTriggered());

        //set message context with matching resource but containing a sub resource
        endpointReference.setAddress("/sectest/resource2/resource22");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged since resource 2 was defined, but it was not engaged.",
                          internalAPIHandler.isHandleTriggered());

        //set message context with a resource that is not matching
        endpointReference.setAddress("/sectest/resource3");
        internalAPIHandler.invoke(messageContext);
        Assert.assertFalse("Handler should not be engaged since resource 3 was not defined, but it was engaged.",
                           internalAPIHandler.isHandleTriggered());

    }

    /**
     * Tests a handler configuration similar to the following.
     *
     * <handler name="SampleInternalApiHandlerWithAllResources"
     *          class="internal.http.api.SampleInternalApiHandlerWithAllResources">
     *  <resources>
     *      <resource>/</resource>
     *  </resources>
     *</handler>
     */
    @Test
    public void testHandledWithAllResources() {

        //Create test message context
        MessageContext messageContext = new TestMessageContext();
        EndpointReference endpointReference = new EndpointReference();
        messageContext.setTo(endpointReference);

        TestSecurityHandler internalAPIHandler = new TestSecurityHandler("/sectest");
        List<String> resources = new ArrayList<>();
        resources.add("/");
        internalAPIHandler.setResources(resources);

        //set message context with matching resource
        endpointReference.setAddress("/sectest/resource1");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged since all resources are defined, but it was not engaged.",
                          internalAPIHandler.isHandleTriggered());
    }

    /**
     * An HTTP/1.1 absolute-form request target (e.g. "https://host:port/management/configs") must be recognized
     * as matching the handler's context the same way the equivalent origin-form path would -- not fail a raw
     * prefix check and skip authentication merely because the target starts with "https://" instead of "/".
     * This is the exact request shape used to bypass authentication on the management API (MI-460).
     */
    @Test
    public void testHandledWithAbsoluteFormTarget() {

        //Create test message context
        MessageContext messageContext = new TestMessageContext();
        EndpointReference endpointReference = new EndpointReference();
        messageContext.setTo(endpointReference);

        TestSecurityHandler internalAPIHandler = new TestSecurityHandler(Constants.REST_API_CONTEXT);
        internalAPIHandler.setResources(new ArrayList<>());

        //absolute-form target resolving to a path under the management API context
        endpointReference.setAddress("https://localhost:9164/management/configs");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("Handler should be engaged for an absolute-form request target that resolves to a "
                           + "matching path, but it was not engaged.", internalAPIHandler.isHandleTriggered());

        //absolute-form target resolving to a path outside the management API context
        endpointReference.setAddress("https://localhost:9164/othercontext/resource1");
        internalAPIHandler.invoke(messageContext);
        Assert.assertFalse("Handler should not be engaged for an absolute-form request target that resolves to "
                            + "a non-matching path, but it was engaged.", internalAPIHandler.isHandleTriggered());
    }

    /**
     * needsHandling() is only ever reached for a request target that the internal dispatcher (via
     * {@code ApiUtils.getFullRequestPath()}, which uses the lenient {@code java.net.URL}) has already resolved
     * to a path under "/management" -- e.g. "https://localhost:9164/management/%zz" resolves to
     * "/management/%zz" there and gets routed to this handler. But {@code Utils.getNormalizedResourcePath()}
     * parses the same target with the strict {@code java.net.URI}, which rejects the malformed "%zz"
     * percent-escape. This divergence must fail closed, i.e. still be treated as needing authentication,
     * rather than being silently skipped because its path could not be determined here.
     */
    @Test
    public void testFailsClosedForUnparseableTarget() {

        MessageContext messageContext = new TestMessageContext();
        EndpointReference endpointReference = new EndpointReference();
        messageContext.setTo(endpointReference);

        TestSecurityHandler internalAPIHandler = new TestSecurityHandler(Constants.REST_API_CONTEXT);
        internalAPIHandler.setResources(new ArrayList<>());

        endpointReference.setAddress("https://localhost:9164/management/%zz");
        internalAPIHandler.invoke(messageContext);
        Assert.assertTrue("A request target that cannot be parsed as a URI must fail closed (be treated as "
                           + "needing authentication), but it was not.", internalAPIHandler.isHandleTriggered());
    }
}
