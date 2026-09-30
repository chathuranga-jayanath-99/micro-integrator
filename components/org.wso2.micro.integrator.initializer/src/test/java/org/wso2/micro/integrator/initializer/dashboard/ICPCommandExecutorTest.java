/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.initializer.dashboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ICPCommandExecutor, which performs the management calls the ICP tunnels
 * through heartbeat responses.
 * <p>
 * What is tested here is everything the executor decides on its own: what it refuses, what
 * it declines to run twice, and how it hands a body back. The call itself is a loopback HTTP
 * request to this node's own management listener and is covered end to end by the ICP's QA
 * labs, not here.
 * <p>
 * Every case below therefore stops before the listener is contacted — which is also the
 * point: a refusal must not depend on the server being up.
 */
public class ICPCommandExecutorTest {

    private static final String RUNTIME_ID = "runtime-1";
    private static final String TOKEN = "a.b.c";

    @Test
    public void testRunOne_PathOutsideManagement_IsRefusedNotPerformed() {
        // The ICP confines the path too. Both sides check, deliberately: a tunneled
        // method-and-path envelope is otherwise a relay into anything this process can reach.
        JsonObject result = ICPCommandExecutor.runOne(
                command("cmd-outside", "GET", "/internal/apis"), RUNTIME_ID, TOKEN);

        assertNotNull("A refusal is still a result the ICP can hand to its caller", result);
        assertEquals("FAILED", result.get("status").getAsString());
        assertEquals(403, result.get("httpStatus").getAsInt());
        assertEquals(RUNTIME_ID, result.get("runtimeId").getAsString());
    }

    @Test
    public void testRunOne_DotSegmentsOutOfManagement_AreRefused() {
        // Each passes a plain startsWith("/management/") and resolves outside it.
        String[] escapes = {
                "/management/../internal/apis",
                "/management/./../internal",
                "/management/%2e%2e/internal",
                "/management/%2E%2E%2Finternal",
                "/management/..%5cinternal",
                "https://evil.example/management/apis",
                "//evil.example/management/apis",
        };
        for (String path : escapes) {
            JsonObject result = ICPCommandExecutor.runOne(
                    command("cmd-" + path, "GET", path), RUNTIME_ID, TOKEN);
            assertEquals(path, 403, result.get("httpStatus").getAsInt());
        }
    }

    @Test
    public void testConfinedPath_KeepsPathsInsideManagementAndTheirQuery() {
        assertEquals("/management/apis?apiName=a%20b",
                ICPCommandExecutor.confinedPath("/management/apis?apiName=a%20b"));
        assertEquals("/management/logging",
                ICPCommandExecutor.confinedPath("/management/apis/../logging"));
        assertNull(ICPCommandExecutor.confinedPath("/management"));
    }

    @Test
    public void testRunOne_UnsupportedMethod_IsRefusedWithoutTouchingTheListener() {
        JsonObject result = ICPCommandExecutor.runOne(
                command("cmd-verb", "TRACE", "/management/logging"), RUNTIME_ID, TOKEN);

        assertEquals(405, result.get("httpStatus").getAsInt());
        assertEquals("FAILED", result.get("status").getAsString());
    }

    @Test
    public void testRunOne_NoParams_FailsRatherThanThrows() {
        JsonObject payload = new JsonObject();
        payload.addProperty("commandId", "cmd-empty");
        JsonObject result = ICPCommandExecutor.runOne(wrap(payload), RUNTIME_ID, TOKEN);

        assertEquals(400, result.get("httpStatus").getAsInt());
    }

    @Test
    public void testRunOne_ExpiredCommand_IsDroppedUnexecuted() {
        // The ICP-side caller has already been told this read failed, so a late mutation is
        // worse than none. Nothing is posted back: null is "there is no outcome to report".
        JsonObject expired = command("cmd-expired", "PATCH", "/management/logging");
        JsonObject payload = payloadOf(expired);
        payload.addProperty("deadline", Instant.now().minus(1, ChronoUnit.MINUTES).toString());
        expired.addProperty("payload", payload.toString());

        assertNull(ICPCommandExecutor.runOne(expired, RUNTIME_ID, TOKEN));
    }

    @Test
    public void testRunOne_RedeliveredCommand_ReplaysItsStoredResult() {
        // A result lost in flight makes the ICP re-offer the command. Performing it again
        // would delete a user twice; the stored outcome is returned instead.
        JsonObject first = ICPCommandExecutor.runOne(
                command("cmd-replay", "GET", "/etc/passwd"), RUNTIME_ID, TOKEN);
        JsonObject second = ICPCommandExecutor.runOne(
                command("cmd-replay", "GET", "/etc/passwd"), RUNTIME_ID, TOKEN);

        assertSame("The second delivery must replay the first outcome", first, second);
    }

    @Test
    public void testResultCache_EvictsOldestOnceTheSizeBudgetIsPassed() {
        JsonObject result = resultOfLength(100);
        long chars = result.toString().length();
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(256, chars * 2);

        cache.put("a", result, true);
        cache.put("b", resultOfLength(100), true);
        cache.put("c", resultOfLength(100), true);

        assertNull("The oldest goes first", cache.get("a"));
        assertNotNull(cache.get("b"));
        assertNotNull(cache.get("c"));
        assertEquals(chars * 2, cache.totalChars());
    }

    @Test
    public void testResultCache_EvictsOldestOnceTheCountIsPassed() {
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(2, Long.MAX_VALUE);

        cache.put("a", resultOfLength(1), true);
        cache.put("b", resultOfLength(1), true);
        cache.put("c", resultOfLength(1), true);

        assertEquals(2, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    public void testResultCache_OversizedReadIsNotKeptAndEvictsNothing() {
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(256, 500);

        cache.put("small", resultOfLength(10), true);
        cache.put("huge", resultOfLength(10_000), true);

        assertNull("A read over the whole budget is simply performed again", cache.get("huge"));
        assertNotNull("and does not push out what is already held", cache.get("small"));
    }

    @Test
    public void testResultCache_OversizedMutationKeepsItsOutcomeWithoutTheBody() {
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(256, 500);

        cache.put("delete", resultOfLength(10_000), false);

        JsonObject replay = cache.get("delete");
        assertNotNull("A redelivered mutation must still replay rather than run again", replay);
        assertEquals("COMPLETED", replay.get("status").getAsString());
        assertEquals(200, replay.get("httpStatus").getAsInt());
        assertTrue(cache.totalChars() <= 500);
    }

    @Test
    public void testResultCache_SizePressureSlimsMutationsAndDropsReads() {
        JsonObject sample = resultOfLength(1_000);
        long chars = sample.toString().length();
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(256, chars * 2);

        cache.put("mutation", sample, false);
        cache.put("read", resultOfLength(1_000), true);
        cache.put("newest", resultOfLength(1_000), true);

        JsonObject mutation = cache.get("mutation");
        assertNotNull("The oldest mutation keeps its record", mutation);
        assertTrue("but gives up its body", mutation.toString().length() < chars);
        assertNull("A read goes entirely once slimming the mutation is not enough", cache.get("read"));
        assertNotNull(cache.get("newest"));
        assertTrue(cache.totalChars() <= chars * 2);
    }

    @Test
    public void testResultCacheMaxChars_ConfiguredMegabytesOrTheDefault() {
        long mb = 1024L * 1024;
        Map<String, Object> configs = new HashMap<>();
        assertEquals("Unset", 8 * mb, ICPCommandExecutor.resultCacheMaxChars(configs));
        assertEquals("No configs parsed yet", 8 * mb, ICPCommandExecutor.resultCacheMaxChars(null));

        configs.put("icp_config.command_result_cache_size_mb", 32L);
        assertEquals(32 * mb, ICPCommandExecutor.resultCacheMaxChars(configs));

        for (Object invalid : new Object[] {0L, -5L, "lots", "8m", Long.MAX_VALUE}) {
            configs.put("icp_config.command_result_cache_size_mb", invalid);
            assertEquals(String.valueOf(invalid), 8 * mb, ICPCommandExecutor.resultCacheMaxChars(configs));
        }
    }

    @Test
    public void testRunOne_UnreadableCommand_IsIgnored() {
        JsonObject unreadable = new JsonObject();
        unreadable.addProperty("action", "MI_MGMT");
        unreadable.addProperty("payload", "not json");
        assertNull(ICPCommandExecutor.runOne(unreadable, RUNTIME_ID, TOKEN));

        JsonObject anonymous = new JsonObject();
        anonymous.addProperty("action", "MI_MGMT");
        anonymous.addProperty("payload", new JsonObject().toString());
        assertNull("Without a command id there is nothing to report an outcome under",
                ICPCommandExecutor.runOne(anonymous, RUNTIME_ID, TOKEN));
    }

    @Test
    public void testAsJson_TextStaysTextEvenWhenItLooksLikeJson() {
        // Decided by the response's Content-Type, never by a parse attempt: a log file whose
        // first line happens to be a bare number is text, and guessing turned it into one.
        JsonElement logFile = ICPCommandExecutor.asJson("12345", false);
        assertTrue("A text body travels as a JSON string", logFile.isJsonPrimitive());
        assertEquals("12345", logFile.getAsString());

        JsonElement json = ICPCommandExecutor.asJson("{\"count\":2}", true);
        assertTrue(json.isJsonObject());
        assertEquals(2, json.getAsJsonObject().get("count").getAsInt());
    }

    @Test
    public void testAsJson_EmptyBodyIsNullAndMalformedJsonSurvivesAsText() {
        assertTrue("A 200 with no body has still answered",
                ICPCommandExecutor.asJson("", true).isJsonNull());
        assertEquals("truncated {", ICPCommandExecutor.asJson("truncated {", true).getAsString());
    }

    @Test
    public void testIsPast_MalformedDeadlineIsTreatedAsExpired() {
        assertTrue(ICPCommandExecutor.isPast(Instant.now().minusSeconds(1).toString()));
        assertFalse(ICPCommandExecutor.isPast(Instant.now().plusSeconds(60).toString()));
        assertTrue("An unassessable deadline is no licence to run without one",
                ICPCommandExecutor.isPast("whenever"));
    }

    @Test
    public void testMethodFor_KnownVerbsOnly() {
        assertEquals("GET", ICPCommandExecutor.methodFor("get"));
        assertEquals("DELETE", ICPCommandExecutor.methodFor("DELETE"));
        assertNull(ICPCommandExecutor.methodFor("CONNECT"));
    }

    private static JsonObject resultOfLength(int bodyChars) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "COMPLETED");
        result.addProperty("httpStatus", 200);
        result.addProperty("body", new String(new char[bodyChars]).replace('\0', 'x'));
        return result;
    }

    @Test
    public void testWriteCommandId_OnlyManagementWritesAreHeld() {
        // A read refused while busy is re-offered and harmless to rerun; a write is what a
        // refusal delays, so only writes are kept for after the running batch.
        assertEquals("w1", ICPCommandExecutor.writeCommandId(command("w1", "POST", "/management/apis")));
        assertEquals("w2", ICPCommandExecutor.writeCommandId(command("w2", "PATCH", "/management/logging")));
        assertEquals("w3", ICPCommandExecutor.writeCommandId(command("w3", "DELETE", "/management/users/a")));
        assertNull(ICPCommandExecutor.writeCommandId(command("r1", "GET", "/management/logging")));

        JsonObject otherFeature = command("x1", "POST", "/management/apis");
        otherFeature.addProperty("action", "WORKFLOW_MGMT");
        assertNull(ICPCommandExecutor.writeCommandId(otherFeature));

        JsonObject unreadable = new JsonObject();
        unreadable.addProperty("action", "MI_MGMT");
        unreadable.addProperty("payload", "not json");
        assertNull(ICPCommandExecutor.writeCommandId(unreadable));
    }

    @Test
    public void testHoldWrites_KeepsEachWriteOnceAndLeavesReadsToBeReoffered() {
        ICPCommandExecutor.clearHeldWrites();
        try {
            com.google.gson.JsonArray batch = new com.google.gson.JsonArray();
            batch.add(command("w1", "POST", "/management/sequences"));
            batch.add(command("r1", "GET", "/management/logs"));
            batch.add(command("w2", "POST", "/management/apis"));
            assertEquals(2, ICPCommandExecutor.holdWrites(batch, "https://icp", RUNTIME_ID, TOKEN));

            // The same write offered again while it still waits is not queued a second time.
            com.google.gson.JsonArray redelivered = new com.google.gson.JsonArray();
            redelivered.add(command("w1", "POST", "/management/sequences"));
            assertEquals(2, ICPCommandExecutor.holdWrites(redelivered, "https://icp", RUNTIME_ID, TOKEN));
        } finally {
            ICPCommandExecutor.clearHeldWrites();
        }
    }

    @Test
    public void testHoldWrites_IsBoundedAndPastTheCapLeavesTheRestToTheIcp() {
        ICPCommandExecutor.clearHeldWrites();
        try {
            com.google.gson.JsonArray flood = new com.google.gson.JsonArray();
            for (int i = 0; i < 100; i++) {
                flood.add(command("w" + i, "POST", "/management/apis"));
            }
            assertEquals(64, ICPCommandExecutor.holdWrites(flood, "https://icp", RUNTIME_ID, TOKEN));
            assertEquals(64, ICPCommandExecutor.heldWriteCount());
        } finally {
            ICPCommandExecutor.clearHeldWrites();
        }
    }

    @Test
    public void testResultCache_ExpiredRecordsGoBeforeALiveWriteIsEvicted() {
        // Past its deadline a command is never run again, so its record protects nothing. A
        // burst of results must push those out, not the replay record of a write that can
        // still be redelivered — that write would otherwise run twice.
        ICPCommandExecutor.ResultCache cache = new ICPCommandExecutor.ResultCache(3, Long.MAX_VALUE);
        long past = System.currentTimeMillis() - 1_000;
        long future = System.currentTimeMillis() + 60_000;
        cache.put("expired-1", resultOfLength(10), false, past);
        cache.put("expired-2", resultOfLength(10), false, past);
        cache.put("live-write", resultOfLength(10), false, future);
        cache.put("read-1", resultOfLength(10), true, future);
        cache.put("read-2", resultOfLength(10), true, future);

        assertNotNull("A live write's replay record must survive", cache.get("live-write"));
        assertNull(cache.get("expired-1"));
        assertNull(cache.get("expired-2"));
        assertEquals(3, cache.size());
    }

    @Test
    public void testDeadlineMillis_ParsesTheDeadlineOrTreatsItAsOpenEnded() {
        assertEquals(Instant.parse("2026-09-30T10:00:00Z").toEpochMilli(),
                ICPCommandExecutor.deadlineMillis("2026-09-30T10:00:00Z"));
        assertEquals(Long.MAX_VALUE, ICPCommandExecutor.deadlineMillis(null));
        assertEquals(Long.MAX_VALUE, ICPCommandExecutor.deadlineMillis("not a time"));
    }

    @Test
    public void testHashIsReusable_OnlyWithinTheConfiguredInterval() {
        // Boosted rounds come every second; collecting every artifact that often is the cost
        // this avoids. At the configured cadence the hash is computed afresh, as it always was.
        long now = 1_000_000L;
        assertTrue(ICPHeartBeatComponent.hashIsReusable("h", now - 1_000, now, 10));
        assertFalse(ICPHeartBeatComponent.hashIsReusable("h", now - 10_000, now, 10));
        assertFalse(ICPHeartBeatComponent.hashIsReusable(null, now - 1_000, now, 10));
    }

    // The same vectors are asserted by the ICP's and the BI bridge's tests, computed
    // independently; the three sides interoperate only if all three produce these.
    private static final String SIGNED_PAYLOAD = "{\"commandId\":\"mio-1.runtime-1\",\"operation\":\"management\","
            + "\"params\":{\"method\":\"POST\",\"path\":\"/management/sequences\",\"body\":{\"name\":\"fault\","
            + "\"statistics\":\"enable\"}},\"deadline\":\"2026-09-30T10:00:00Z\"}";
    private static final String SIGNATURE = "3zoKUUS0CVRD/4ccF6nqssTJfGQgx7sPyj8nG980VRA=";
    private static final byte[] KEY = "key-material-that-is-at-least-32-bytes-long"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private static JsonObject signed(String payload, String signature) {
        JsonObject command = new JsonObject();
        command.addProperty("commandId", "mio-1.runtime-1");
        command.addProperty("action", "MI_MGMT");
        JsonObject target = new JsonObject();
        target.addProperty("name", "management");
        command.add("targetArtifact", target);
        command.addProperty("payload", payload);
        if (signature != null) {
            command.addProperty("signature", signature);
        }
        return command;
    }

    @Test
    public void testSignatureRefusal_TheIcpsSignatureVerifies() {
        assertNull(ICPCommandExecutor.signatureRefusal(signed(SIGNED_PAYLOAD, SIGNATURE), "runtime-1", KEY, true));
    }

    @Test
    public void testSignatureRefusal_ATamperedOrMisdirectedCommandIsRefused() {
        assertNotNull("A changed payload must not verify", ICPCommandExecutor.signatureRefusal(
                signed(SIGNED_PAYLOAD.replace("enable", "disable"), SIGNATURE), "runtime-1", KEY, false));
        assertNotNull("A command signed for another replica must not verify", ICPCommandExecutor.signatureRefusal(
                signed(SIGNED_PAYLOAD, SIGNATURE), "runtime-2", KEY, false));
        assertNotNull("A signature that is not Base64 must not verify", ICPCommandExecutor.signatureRefusal(
                signed(SIGNED_PAYLOAD, "%%%"), "runtime-1", KEY, false));
        assertNotNull("A signed command cannot be verified without the key", ICPCommandExecutor.signatureRefusal(
                signed(SIGNED_PAYLOAD, SIGNATURE), "runtime-1", null, false));
        JsonObject renamed = signed(SIGNED_PAYLOAD, SIGNATURE);
        renamed.addProperty("commandId", "mio-2.runtime-1");
        assertNotNull("The command id is signed too", ICPCommandExecutor.signatureRefusal(
                renamed, "runtime-1", KEY, false));
    }

    @Test
    public void testSignatureRefusal_UnsignedIsRefusedOnlyWhenRequired() {
        // Off by default for a release, so this MI still takes commands from an ICP that does
        // not sign yet; turning require_signed_commands on closes that.
        assertNull(ICPCommandExecutor.signatureRefusal(signed(SIGNED_PAYLOAD, null), "runtime-1", KEY, false));
        assertNotNull(ICPCommandExecutor.signatureRefusal(signed(SIGNED_PAYLOAD, null), "runtime-1", KEY, true));
    }

    private static JsonObject command(String commandId, String method, String path) {
        JsonObject params = new JsonObject();
        params.addProperty("method", method);
        params.addProperty("path", path);

        JsonObject payload = new JsonObject();
        payload.addProperty("commandId", commandId);
        payload.add("params", params);

        return wrap(payload);
    }

    private static JsonObject wrap(JsonObject payload) {
        JsonObject command = new JsonObject();
        command.addProperty("action", "MI_MGMT");
        command.addProperty("payload", payload.toString());
        return command;
    }

    private static JsonObject payloadOf(JsonObject command) {
        return com.google.gson.JsonParser
                .parseString(command.get("payload").getAsString()).getAsJsonObject();
    }
}
