/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient.EventKind;
import com.redhat.rhjmc.containerjfr.messaging.notifications.NotificationFactory;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;
import com.redhat.rhjmc.containerjfr.platform.TargetDiscoveryEvent;

abstract class AbstractPlatformClient implements PlatformClient {

    static final String NOTIFICATION_CATEGORY = "TargetJvmDiscovery";

    protected final Set<Consumer<TargetDiscoveryEvent>> discoveryListeners;

    protected AbstractPlatformClient(NotificationFactory notificationFactory) {
        this.discoveryListeners = new HashSet<>();

        addTargetDiscoveryListener(
                tde ->
                        notificationFactory
                                .createBuilder()
                                .metaCategory(NOTIFICATION_CATEGORY)
                                .message(
                                        Map.of(
                                                "event",
                                                Map.of(
                                                        "kind",
                                                        tde.getEventKind(),
                                                        "serviceRef",
                                                        tde.getServiceRef())))
                                .build()
                                .send());
    }

    @Override
    public void addTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener) {
        this.discoveryListeners.add(listener);
    }

    @Override
    public void removeTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener) {
        this.discoveryListeners.remove(listener);
    }

    protected void notifyAsyncTargetDiscovery(EventKind eventKind, ServiceRef serviceRef) {
        discoveryListeners.forEach(c -> c.accept(new TargetDiscoveryEvent(eventKind, serviceRef)));
    }
}
