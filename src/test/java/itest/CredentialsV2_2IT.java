/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package itest;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
public class CredentialsV2_2IT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);
    static final List<String> CONTAINERS = new ArrayList<>();
    static final Map<String, String> NULL_RESULT = new HashMap<>();
    static final String REQUEST_URL = "/api/v2.2/credentials";
    static final String MATCH_EXPRESSION = "target.alias == \"es.andrewazor.demo.Main\"";

    static {
        NULL_RESULT.put("result", null);
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
        }
    }

    @Test
    @Order(0)
    void testDeleteThrowsOnNonExistentCredentials() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(REQUEST_URL + "/0")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    @Order(1)
    void testAddCredentialsThrowsOnEmptyForm() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();

        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    @Order(2)
    void testAddCredentialsThrowsOnOmittedMatchExpression() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", "admin");
        form.add("password", "adminpass123");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @ParameterizedTest
    @Order(3)
    @ValueSource(strings = {"", " ", "\n", "==", "target.alias ==", "invalid text"})
    void testAddCredentialsThrowsOnInvalidMatchExpression(String matchExpression) throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", matchExpression);
        form.add("username", "admin");
        form.add("password", "adminpass123");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    @Order(4)
    void testAddCredentialsThrowsOnOmittedUsername() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", MATCH_EXPRESSION);
        form.add("password", "adminpass123");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @ParameterizedTest
    @Order(5)
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void testAddCredentialsThrowsOnBlankUsername(String username) throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", MATCH_EXPRESSION);
        form.add("username", username);
        form.add("password", "adminpass123");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    @Order(6)
    void testAddCredentialsThrowsOnOmittedPassword() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", MATCH_EXPRESSION);
        form.add("username", "admin");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @ParameterizedTest
    @Order(7)
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void testAddCredentialsThrowsOnBlankPassword(String password) throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", MATCH_EXPRESSION);
        form.add("username", "admin");
        form.add("password", password);
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    @Order(7)
    void testWorkflow() throws Exception {
        List<URI> targetIds = startTargets();

        // we should fail to query active recordings from the targets before adding credentials
        for (URI uri : targetIds) {
            CompletableFuture<Integer> recordingsQueryFuture = new CompletableFuture<>();
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    URLEncodedUtils.formatSegments(uri.toString())))
                    .send(
                            ar -> {
                                if (ar.failed()) {
                                    recordingsQueryFuture.completeExceptionally(ar.cause());
                                    return;
                                }
                                recordingsQueryFuture.complete(ar.result().statusCode());
                            });
            Integer queryResponseStatusCode =
                    recordingsQueryFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(queryResponseStatusCode, Matchers.equalTo(427));
        }

        // Get target credentials list should be empty at first
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(REQUEST_URL)
                .send(
                        ar -> {
                            if (ar.succeeded()) {

                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            } else {
                                getResponse.completeExceptionally(ar.cause());
                            }
                        });

        JsonObject expectedEmptyResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                Map.of("result", new ArrayList<>())));

        MatcherAssert.assertThat(
                getResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedEmptyResponse));

        // Post credentials for the new containers
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("matchExpression", MATCH_EXPRESSION);
        form.add("username", "admin");
        form.add("password", "adminpass123");
        webClient
                .post(REQUEST_URL)
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, postResponse)) {
                                postResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.PLAINTEXT.mime(), "status", "Created"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedResponse));

        // Check credentials list reflects just-added definition
        CompletableFuture<JsonObject> getResponse2 = new CompletableFuture<>();
        webClient
                .get(REQUEST_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse2)) {
                                getResponse2.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject response = getResponse2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(response.getJsonObject("meta"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("type"),
                Matchers.equalTo("application/json"));
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("status"), Matchers.equalTo("OK"));

        MatcherAssert.assertThat(response.getJsonObject("data"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("data").getValue("result"), Matchers.notNullValue());

        List<StoredCredential> actualList =
                gson.fromJson(
                        response.getJsonObject("data").getValue("result").toString(),
                        new TypeToken<List<StoredCredential>>() {}.getType());

        MatcherAssert.assertThat(actualList, Matchers.hasSize(1));
        StoredCredential storedCredential = actualList.get(0);
        MatcherAssert.assertThat(
                storedCredential.matchExpression, Matchers.equalTo(MATCH_EXPRESSION));
        MatcherAssert.assertThat(storedCredential.id, Matchers.greaterThanOrEqualTo(0));

        // Check that resolving the credential includes our targets
        CompletableFuture<JsonObject> resolveResponse = new CompletableFuture<>();
        webClient
                .get(REQUEST_URL + "/" + storedCredential.id)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, resolveResponse)) {
                                resolveResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject resolutionResponse =
                resolveResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(resolutionResponse.getJsonObject("meta"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                resolutionResponse.getJsonObject("meta").getString("type"),
                Matchers.equalTo("application/json"));
        MatcherAssert.assertThat(
                resolutionResponse.getJsonObject("meta").getString("status"),
                Matchers.equalTo("OK"));

        MatcherAssert.assertThat(resolutionResponse.getJsonObject("data"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                resolutionResponse.getJsonObject("data").getValue("result"),
                Matchers.notNullValue());

        MatchedCredential matchedCredential =
                gson.fromJson(
                        resolutionResponse.getJsonObject("data").getValue("result").toString(),
                        new TypeToken<MatchedCredential>() {}.getType());

        MatcherAssert.assertThat(
                matchedCredential.matchExpression, Matchers.equalTo(MATCH_EXPRESSION));

        Set<ServiceRef> expectedResolvedTargets = new HashSet<ServiceRef>();
        ServiceRef expectedTarget1 =
                new ServiceRef(
                        new URIBuilder("service:jmx:rmi:///jndi/rmi://cryostat-itests:9094/jmxrmi")
                                .build(),
                        "es.andrewazor.demo.Main");
        expectedTarget1.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.HOST,
                        "cryostat-itests",
                        AnnotationKey.PORT,
                        "9094",
                        AnnotationKey.JAVA_MAIN,
                        "es.andrewazor.demo.Main"));
        expectedResolvedTargets.add(expectedTarget1);
        ServiceRef expectedTarget2 =
                new ServiceRef(
                        new URIBuilder("service:jmx:rmi:///jndi/rmi://cryostat-itests:9095/jmxrmi")
                                .build(),
                        "es.andrewazor.demo.Main");
        expectedTarget2.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.HOST,
                        "cryostat-itests",
                        AnnotationKey.PORT,
                        "9095",
                        AnnotationKey.JAVA_MAIN,
                        "es.andrewazor.demo.Main"));
        expectedResolvedTargets.add(expectedTarget2);

        MatcherAssert.assertThat(
                matchedCredential.targets, Matchers.equalTo(expectedResolvedTargets));

        // we should now be able to query active recordings from the targets
        for (URI uri : targetIds) {
            CompletableFuture<JsonArray> recordingsQueryFuture = new CompletableFuture<>();
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    URLEncodedUtils.formatSegments(uri.toString())))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, resolveResponse)) {
                                    recordingsQueryFuture.complete(ar.result().bodyAsJsonArray());
                                }
                            });
            JsonArray queryResponse =
                    recordingsQueryFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertTrue(queryResponse.isEmpty());
        }
    }

    @Test
    @Order(8)
    void testDeletion() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(REQUEST_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject response = getResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<StoredCredential> actualList =
                gson.fromJson(
                        response.getJsonObject("data").getValue("result").toString(),
                        new TypeToken<List<StoredCredential>>() {}.getType());

        MatcherAssert.assertThat(actualList, Matchers.hasSize(1));
        StoredCredential storedCredential = actualList.get(0);

        // Delete credentials to clean up
        CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
        webClient
                .delete(REQUEST_URL + "/" + storedCredential.id)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, deleteResponse)) {
                                deleteResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedDeleteResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.PLAINTEXT.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                deleteResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedDeleteResponse));
    }

    private List<URI> startTargets() throws Exception {
        List<Podman.ImageSpec> specs = new ArrayList<>();
        specs.add(
                new Podman.ImageSpec(
                        "quay.io/andrewazores/vertx-fib-demo:0.7.0",
                        Map.of("JMX_PORT", String.valueOf(9094), "USE_AUTH", "true")));
        specs.add(
                new Podman.ImageSpec(
                        "quay.io/andrewazores/vertx-fib-demo:0.7.0",
                        Map.of("JMX_PORT", String.valueOf(9095), "USE_AUTH", "true")));
        for (Podman.ImageSpec spec : specs) {
            CONTAINERS.add(Podman.run(spec));
        }
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(specs.size());

        return specs.stream()
                .map(
                        spec -> {
                            int port = Integer.valueOf(spec.envs.get("JMX_PORT"));
                            return URI.create(
                                    String.format(
                                            "service:jmx:rmi:///jndi/rmi://cryostat-itests:%d/jmxrmi",
                                            port));
                        })
                .collect(Collectors.toList());
    }

    private static class StoredCredential {
        int id;
        String matchExpression;
    }

    private static class MatchedCredential {
        String matchExpression;
        Set<ServiceRef> targets;
    }
}