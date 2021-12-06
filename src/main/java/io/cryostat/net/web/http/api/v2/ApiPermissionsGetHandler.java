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
package io.cryostat.net.web.http.api.v2;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpMethod;

class ApiPermissionsGetHandler
        extends AbstractV2RequestHandler<ApiPermissionsGetHandler.ApiResponse> {

    private final Lazy<WebServer> webServer;
    private final Lazy<Set<RequestHandler>> handlers;

    private static final Set<GroupResource> PERMISSION_NOT_REQUIRED =
            Set.of(GroupResource.PERMISSION_NOT_REQUIRED);

    @Inject
    ApiPermissionsGetHandler(
            Lazy<WebServer> webServer,
            Lazy<Set<RequestHandler>> handlers,
            AuthManager auth,
            Gson gson) {
        super(auth, gson);
        this.webServer = webServer;
        this.handlers = handlers;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public String path() {
        return basePath() + "api/permissions";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public IntermediateResponse<ApiResponse> handle(RequestParameters requestParams)
            throws Exception {
        List<String> resourceActions =
                handlers.get().stream()
                        .filter(RequestHandler::isAvailable)
                        .sorted((h1, h2) -> h1.path().compareTo(h2.path()))
                        .map(h -> this.toStringSet(h.resourceActions()))
                        .flatMap(Set::stream)
                        .distinct()
                        .collect(Collectors.toList());

        Collections.sort(resourceActions);

        URL resourceFilePath = new URL(webServer.get().getHostUrl(), "HTTP_API.md");

        return new IntermediateResponse<ApiResponse>()
                .body(new ApiResponse(resourceFilePath, resourceActions));
    }

    @SuppressFBWarnings("URF_UNREAD_FIELD")
    static class ApiResponse {
        @SerializedName("overview")
        final URL resourceFilePath;

        @SerializedName("endpoints")
        final List<String> resourceActions;

        ApiResponse(URL resourceFilePath, List<String> resourceActions) {
            this.resourceFilePath = resourceFilePath;
            this.resourceActions = resourceActions;
        }
    }

    private Set<String> toStringSet(Set<ResourceAction> actions) {
        Set<String> set = new HashSet<String>();

        for (ResourceAction action : actions) {
            set.add(resourceDescription(action));
        }

        return set;
    }

    private String resourceDescription(ResourceAction resourceAction) {
        Set<GroupResource> resources = map(resourceAction.getResource());
        String verb = map(resourceAction.getVerb());

        return String.format("%s: %s", resources.toString(), verb);
    }

    private static Set<GroupResource> map(ResourceType resource) {
        switch (resource) {
            case TARGET:
                return Set.of(GroupResource.FLIGHTRECORDERS);
            case RECORDING:
                return Set.of(GroupResource.RECORDINGS);
            case CERTIFICATE:
                return Set.of(
                        GroupResource.DEPLOYMENTS, GroupResource.PODS, GroupResource.CRYOSTATS);
            case CREDENTIALS:
                return Set.of(GroupResource.CRYOSTATS);
            case TEMPLATE:
            case REPORT:
            case RULE:
            default:
                return PERMISSION_NOT_REQUIRED;
        }
    }

    private static String map(ResourceVerb verb) {
        switch (verb) {
            case CREATE:
                return "create";
            case READ:
                return "get";
            case UPDATE:
                return "patch";
            case DELETE:
                return "delete";
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown resource verb \"%s\"", verb));
        }
    }

    private static enum GroupResource {
        DEPLOYMENTS("apps", "deployments"),
        PODS("", "pods"),
        CRYOSTATS("operator.cryostat.io", "cryostats"),
        FLIGHTRECORDERS("operator.cryostat.io", "flightrecorders"),
        RECORDINGS("operator.cryostat.io", "recordings"),
        PERMISSION_NOT_REQUIRED("", "PERMISSION_NOT_REQUIRED"),
        ;

        private final String group;
        private final String resource;

        private GroupResource(String group, String resource) {
            this.group = group;
            this.resource = resource;
        }

        public String getGroup() {
            return group;
        }

        public String getResource() {
            return resource;
        }
    }
}
