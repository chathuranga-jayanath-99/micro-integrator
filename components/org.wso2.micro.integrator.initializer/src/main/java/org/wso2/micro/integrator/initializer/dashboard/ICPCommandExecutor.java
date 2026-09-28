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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.wso2.carbon.inbound.endpoint.internal.http.api.ConfigurationLoader;
import org.wso2.config.mapper.ConfigParser;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes management commands the ICP tunnels through heartbeat responses.
 * <p>
 * MI behind a load balancer is reachable only outbound: nothing routes from the ICP to a
 * particular replica's management port. So the ICP queues the call, hands it over in the
 * response to a heartbeat this node made, and this class performs it against MI's own
 * loopback management API and posts the outcome back to {@code POST /icp/commandResult}.
 * <p>
 * The loopback call, rather than invoking the management resources directly, is what keeps
 * this small: all 38 resources, their authorization handlers and their audit logging apply
 * unchanged, and a management API that grows a resource grows this with it for free.
 */
class ICPCommandExecutor {

    private static final Log log = LogFactory.getLog(ICPCommandExecutor.class);

    private static final String ACTION_MI_MGMT = "MI_MGMT";
    private static final String MANAGEMENT_PATH_PREFIX = "/management/";
    private static final String COMMAND_RESULT_ENDPOINT = "/icp/commandResult";

    /**
     * Bounds on every call this class makes. A management resource that never answers used to
     * block the heartbeat thread in a socket read forever: heartbeats stopped, and the ICP
     * declared a healthy runtime offline a minute later. Nothing here may be unbounded.
     */
    private static final RequestConfig TIMEOUTS = RequestConfig.custom()
            .setConnectTimeout(5_000)
            .setConnectionRequestTimeout(5_000)
            .setSocketTimeout(30_000)
            .build();

    /**
     * Commands run here and not on the heartbeat thread, so a slow one delays other commands
     * but never the heartbeat. The runtime staying visibly alive while it works matters more
     * than any single command finishing.
     * <p>
     * One thread, and a batch is refused while the previous one runs: that is the
     * back-pressure that stops the ICP handing over work faster than it can be executed. A
     * refused batch is not lost — its fetches are re-offered on a later heartbeat, and the
     * result cache turns a redelivery into a replay.
     */
    private static final ExecutorService COMMAND_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ICP-Command");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean batchInFlight = new AtomicBoolean(false);

    /**
     * Outcomes of recently executed commands, so a redelivered commandId — one whose result
     * was lost in flight — replays its stored result instead of performing the operation a
     * second time. Bounded by count and by size; see {@link ResultCache} for what each bound
     * gives up.
     * <p>
     * The size bound is what matters: a result carries the whole response body, and a few
     * hundred downloaded log files held for replay would otherwise sit on the heap. It is
     * {@code icp_config.command_result_cache_size_mb}, in megabytes of result text.
     */
    private static final int RESULT_CACHE_CAPACITY = 256;
    private static final long MEGABYTE = 1024L * 1024;
    private static final ResultCache executedResults =
            new ResultCache(RESULT_CACHE_CAPACITY, resultCacheMaxChars(ConfigParser.getParsedConfigs()));

    private ICPCommandExecutor() {
    }

    /**
     * Executes every management command in a heartbeat response and posts each outcome.
     * <p>
     * Commands of other kinds are ignored rather than refused: the response is shared with
     * features this runtime does not implement, and an unknown action is the ICP talking to
     * a mixed fleet, not an error.
     *
     * @param commands the {@code commands} array from the heartbeat response, may be null
     * @param icpUrl   base URL of the ICP, for posting results
     * @param runtimeId this runtime's id, which the ICP uses to fence the result
     * @param jwtToken the HMAC token the heartbeat component already holds. It serves both
     *                 hops: the management API's JWT handler accepts a token issued with the
     *                 shared ICP secret, so the loopback call authenticates as the same
     *                 'icp-service' identity a direct call from the ICP would have used
     */
    static void execute(JsonArray commands, String icpUrl, String runtimeId, String jwtToken) {
        if (commands == null || commands.size() == 0) {
            return;
        }
        if (!batchInFlight.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug("Still executing the previous ICP command batch; this one will be re-offered.");
            }
            return;
        }
        JsonArray batch = commands.deepCopy();
        COMMAND_POOL.submit(() -> {
            try {
                for (JsonElement element : batch) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject command = element.getAsJsonObject();
                    if (!ACTION_MI_MGMT.equals(optString(command, "action"))) {
                        continue;
                    }
                    JsonObject result = runOne(command, runtimeId, jwtToken);
                    if (result != null) {
                        postResult(result, icpUrl, jwtToken);
                    }
                }
            } catch (Throwable t) {
                log.error("ICP command batch failed", t);
            } finally {
                batchInFlight.set(false);
            }
        });
    }

    /**
     * Runs one command, or replays the result of a redelivery. Never throws: every outcome,
     * including a refusal, is a result the ICP can hand to the caller waiting on it.
     */
    // Package-private, like the three helpers below: every refusal this class can make is
    // decided without a server around it, and the tests exercise them that way.
    static JsonObject runOne(JsonObject command, String runtimeId, String jwtToken) {
        JsonObject payload;
        try {
            payload = JsonParser.parseString(optString(command, "payload")).getAsJsonObject();
        } catch (Exception e) {
            log.error("Ignoring an ICP management command with an unreadable payload", e);
            return null;
        }
        String commandId = optString(payload, "commandId");
        if (commandId == null) {
            log.error("Ignoring an ICP management command with no command id");
            return null;
        }
        JsonObject cached = executedResults.get(commandId);
        if (cached != null) {
            log.info("Replaying the stored result for redelivered ICP command: " + commandId);
            return cached;
        }
        // A command past its deadline must not run: the ICP-side caller has already been told
        // the read failed, and a late mutation is worse than none.
        String deadline = optString(payload, "deadline");
        if (deadline != null && isPast(deadline)) {
            log.warn("Dropping expired ICP command " + commandId + " unexecuted (deadline "
                    + deadline + ")");
            return null;
        }

        JsonObject params = payload.getAsJsonObject("params");
        JsonObject result = params == null
                ? failure(runtimeId, commandId, 400, "Command carried no params")
                : invokeManagementApi(params, runtimeId, commandId, jwtToken);
        boolean rerunnable = params != null && "GET".equalsIgnoreCase(optString(params, "method"));
        executedResults.put(commandId, result, rerunnable);
        return result;
    }

    /**
     * Performs the management call against this node's own loopback listener.
     * <p>
     * The path is confined to {@code /management/} here as well as at the ICP. A tunneled
     * method-and-path envelope is otherwise a relay into anything this process can reach,
     * and the two sides deliberately do not rely on each other to have checked.
     */
    private static JsonObject invokeManagementApi(JsonObject params, String runtimeId,
                                                  String commandId, String jwtToken) {
        String method = optString(params, "method");
        String path = confinedPath(optString(params, "path"));
        if (method == null || path == null) {
            return failure(runtimeId, commandId, 403, "Refused a path outside /management/");
        }
        if (methodFor(method) == null) {
            return failure(runtimeId, commandId, 405, "Unsupported method: " + method);
        }
        // Both refusals above are settled before the listener's port is asked for: what the
        // ICP may ask of this node does not depend on the node being up yet.
        String url = "https://localhost:" + ConfigurationLoader.getInternalInboundHttpsPort() + path;
        try (CloseableHttpClient client = loopbackClient()) {
            HttpRequestBase request = requestFor(method, url);
            request.setHeader("Authorization", "Bearer " + jwtToken);
            request.setHeader("Accept", Constants.HEADER_VALUE_APPLICATION_JSON);
            JsonElement body = params.get("body");
            if (body != null && !body.isJsonNull() && request instanceof HttpEntityEnclosingRequestBase) {
                request.setHeader("Content-Type", Constants.HEADER_VALUE_APPLICATION_JSON);
                ((HttpEntityEnclosingRequestBase) request)
                        .setEntity(new StringEntity(body.toString(), "UTF-8"));
            }
            try (CloseableHttpResponse response = client.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                String text = response.getEntity() == null
                        ? "" : EntityUtils.toString(response.getEntity(), "UTF-8");
                Header contentType = response.getFirstHeader("Content-Type");
                boolean isJson = contentType != null
                        && contentType.getValue() != null
                        && contentType.getValue().toLowerCase(Locale.ROOT).contains("json");
                return result(runtimeId, commandId, "COMPLETED", status, asJson(text, isJson));
            }
        } catch (Exception e) {
            log.error("ICP management command " + commandId + " failed against " + url, e);
            return failure(runtimeId, commandId, 500, e.getMessage());
        }
    }

    /**
     * The path to forward, or {@code null} when it does not stay inside {@code /management/}.
     * <p>
     * A plain prefix check is not enough: {@code /management/../internal} passes it, and the
     * listener resolves the dot segments (percent-encoded or not) before routing. So the
     * prefix is checked on the decoded, normalized path, and the normalized form is what
     * gets forwarded.
     */
    static String confinedPath(String path) {
        if (path == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(path).normalize();
        } catch (URISyntaxException e) {
            return null;
        }
        String decoded = uri.getPath();
        if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                || decoded == null || decoded.indexOf('\\') >= 0) {
            return null;
        }
        try {
            // Decoding can surface dot segments (%2e%2e) that the raw normalization above
            // could not see.
            String normalized = new URI(null, null, decoded, null).normalize().getPath();
            if (!normalized.startsWith(MANAGEMENT_PATH_PREFIX)) {
                return null;
            }
        } catch (URISyntaxException e) {
            return null;
        }
        return uri.toString();
    }

    /**
     * The replay cache's size budget in characters, from
     * {@code icp_config.command_result_cache_size_mb}. Anything but a positive whole number
     * of megabytes falls back to the default.
     */
    static long resultCacheMaxChars(Map<String, Object> configs) {
        long megabytes = Constants.DEFAULT_COMMAND_RESULT_CACHE_SIZE_MB;
        Object configured = configs == null
                ? null : configs.get(Constants.ICP_CONFIG_COMMAND_RESULT_CACHE_SIZE_MB);
        if (configured != null) {
            long parsed;
            try {
                parsed = Long.parseLong(configured.toString().trim());
            } catch (NumberFormatException e) {
                parsed = 0;
            }
            if (parsed > 0 && parsed <= Long.MAX_VALUE / MEGABYTE) {
                megabytes = parsed;
            } else {
                log.warn("Invalid config for '" + Constants.ICP_CONFIG_COMMAND_RESULT_CACHE_SIZE_MB
                        + "': " + configured + ". Using default: " + megabytes);
            }
        }
        return megabytes * MEGABYTE;
    }

    /** Posts one outcome. A lost result is not retried here — the ICP redelivers, and the
     *  cache above turns that redelivery into a replay rather than a second execution. */
    private static void postResult(JsonObject result, String icpUrl, String jwtToken) {
        try (CloseableHttpClient client = ICPHeartBeatComponent.createHttpClient()) {
            HttpPost post = new HttpPost(icpUrl + COMMAND_RESULT_ENDPOINT);
            post.setHeader("Authorization", "Bearer " + jwtToken);
            post.setHeader("Content-type", Constants.HEADER_VALUE_APPLICATION_JSON);
            post.setEntity(new StringEntity(result.toString(), "UTF-8"));
            try (CloseableHttpResponse response = client.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                if (status < 200 || status >= 300) {
                    log.warn("ICP rejected a command result (HTTP " + status + ")");
                }
            }
        } catch (Exception e) {
            log.error("Failed to post an ICP command result", e);
        }
    }

    private static CloseableHttpClient loopbackClient() throws Exception {
        // The certificate on this node's own listener is whatever the operator installed,
        // frequently the self-signed default. Verifying it would make the tunnel depend on a
        // trust chain for a connection that never leaves the loopback interface.
        return HttpClients.custom()
                .setDefaultRequestConfig(TIMEOUTS)
                .setSSLContext(SSLContexts.custom()
                        .loadTrustMaterial(null, (chain, authType) -> true).build())
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
    }

    /** The verb, if this class performs it at all. `null` is the only refusal it reports. */
    static String methodFor(String method) {
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET":
            case "POST":
            case "PUT":
            case "PATCH":
            case "DELETE":
                return method.toUpperCase(Locale.ROOT);
            default:
                return null;
        }
    }

    private static HttpRequestBase requestFor(String method, String url) {
        switch (methodFor(method)) {
            case "GET":    return new HttpGet(url);
            case "POST":   return new HttpPost(url);
            case "PUT":    return new HttpPut(url);
            case "PATCH":  return new HttpPatch(url);
            default:       return new HttpDelete(url);
        }
    }

    /**
     * The management API answers JSON almost everywhere and plain text in a few places (a log
     * file, a fault stack trace). Both travel back verbatim, as a JSON value or as a JSON
     * string; the ICP unwraps the string case back to text for the caller.
     * <p>
     * The decision is the response's own Content-Type and never a parse attempt: a log file
     * whose first line happens to be a bare number is text, and guessing turned it into one.
     */
    static JsonElement asJson(String text, boolean isJson) {
        if (text == null || text.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        if (!isJson) {
            return new JsonPrimitive(text);
        }
        try {
            return JsonParser.parseString(text);
        } catch (Exception e) {
            return new JsonPrimitive(text);
        }
    }

    private static JsonObject result(String runtimeId, String commandId, String status,
                                     int httpStatus, JsonElement body) {
        JsonObject result = new JsonObject();
        result.addProperty("runtimeId", runtimeId);
        result.addProperty("commandId", commandId);
        result.addProperty("status", status);
        result.addProperty("httpStatus", httpStatus);
        result.add("body", body);
        return result;
    }

    private static JsonObject failure(String runtimeId, String commandId, int httpStatus,
                                      String message) {
        JsonObject error = new JsonObject();
        JsonObject detail = new JsonObject();
        detail.addProperty("message", message == null ? "Command execution failed" : message);
        error.add("error", detail);
        return result(runtimeId, commandId, "FAILED", httpStatus, error);
    }

    static boolean isPast(String isoInstant) {
        try {
            return Instant.parse(isoInstant).isBefore(Instant.now());
        } catch (Exception e) {
            // An unassessable deadline is no licence to run without one.
            log.warn("Treating a malformed ICP command deadline as expired: " + isoInstant);
            return true;
        }
    }

    private static String optString(JsonObject object, String member) {
        JsonElement value = object == null ? null : object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /**
     * Insertion-ordered results, bounded by {@code maxEntries} and by {@code maxChars} of
     * serialized length.
     * <p>
     * Only the count bound forgets a command id. The size bound sheds response bodies, oldest
     * first: a rerunnable result (a read) goes entirely, since performing it again on a
     * redelivery is harmless and answers with the full body; any other keeps its outcome
     * without the body, so a redelivered mutation replays that outcome instead of running a
     * second time.
     */
    static final class ResultCache {

        private final int maxEntries;
        private final long maxChars;
        private final Map<String, Stored> results = new LinkedHashMap<>();
        private long totalChars;

        ResultCache(int maxEntries, long maxChars) {
            this.maxEntries = maxEntries;
            this.maxChars = maxChars;
        }

        synchronized JsonObject get(String commandId) {
            Stored stored = results.get(commandId);
            return stored == null ? null : stored.result;
        }

        synchronized void put(String commandId, JsonObject result, boolean rerunnable) {
            Stored previous = results.remove(commandId);
            if (previous != null) {
                totalChars -= previous.chars;
            }
            Stored stored = new Stored(result, rerunnable, false);
            if (stored.chars > maxChars) {
                if (rerunnable) {
                    if (log.isDebugEnabled()) {
                        log.debug("Not keeping the " + stored.chars + "-character result of ICP command "
                                + commandId + " for replay");
                    }
                    return;
                }
                stored = stored.withoutBody();
            }
            results.put(commandId, stored);
            totalChars += stored.chars;

            Iterator<Map.Entry<String, Stored>> oldestFirst = results.entrySet().iterator();
            while (results.size() > maxEntries) {
                totalChars -= oldestFirst.next().getValue().chars;
                oldestFirst.remove();
            }
            oldestFirst = results.entrySet().iterator();
            while (totalChars > maxChars && oldestFirst.hasNext()) {
                Map.Entry<String, Stored> entry = oldestFirst.next();
                Stored held = entry.getValue();
                if (held.rerunnable) {
                    totalChars -= held.chars;
                    oldestFirst.remove();
                } else if (!held.bodyless) {
                    Stored slim = held.withoutBody();
                    if (slim.chars < held.chars) {
                        totalChars -= held.chars - slim.chars;
                        entry.setValue(slim);
                    }
                }
            }
        }

        synchronized int size() {
            return results.size();
        }

        synchronized long totalChars() {
            return totalChars;
        }

        private static final class Stored {

            private static final JsonPrimitive BODY_NOT_KEPT = new JsonPrimitive(
                    "The response body was too large to keep for replay. The command was"
                            + " performed once and is not performed again.");

            private final JsonObject result;
            private final boolean rerunnable;
            private final boolean bodyless;
            private final long chars;

            private Stored(JsonObject result, boolean rerunnable, boolean bodyless) {
                this.result = result;
                this.rerunnable = rerunnable;
                this.bodyless = bodyless;
                this.chars = result.toString().length();
            }

            /** The same outcome (status, HTTP status, ids) with the body replaced by a note. */
            private Stored withoutBody() {
                JsonObject slim = new JsonObject();
                for (Map.Entry<String, JsonElement> member : result.entrySet()) {
                    if (!"body".equals(member.getKey())) {
                        slim.add(member.getKey(), member.getValue());
                    }
                }
                slim.add("body", BODY_NOT_KEPT);
                return new Stored(slim, rerunnable, true);
            }
        }
    }
}
