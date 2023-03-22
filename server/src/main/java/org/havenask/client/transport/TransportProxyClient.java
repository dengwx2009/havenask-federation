/*
*Copyright (c) 2021, Alibaba Group;
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

*   http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright Havenask Contributors. See
 * GitHub history for details.
 */

package org.havenask.client.transport;

import org.havenask.action.ActionType;
import org.havenask.action.ActionListener;
import org.havenask.action.ActionRequest;
import org.havenask.action.ActionRequestBuilder;
import org.havenask.action.ActionResponse;
import org.havenask.action.TransportActionNodeProxy;
import org.havenask.common.settings.Settings;
import org.havenask.transport.TransportService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

final class TransportProxyClient {

    private final TransportClientNodesService nodesService;
    private final Map<ActionType, TransportActionNodeProxy> proxies;

    TransportProxyClient(Settings settings, TransportService transportService,
                                TransportClientNodesService nodesService, List<ActionType> actions) {
        this.nodesService = nodesService;
        Map<ActionType, TransportActionNodeProxy> proxies = new HashMap<>();
        for (ActionType action : actions) {
            proxies.put(action, new TransportActionNodeProxy(settings, action, transportService));
        }
        this.proxies = unmodifiableMap(proxies);
    }

    public <Request extends ActionRequest, Response extends ActionResponse, RequestBuilder extends
        ActionRequestBuilder<Request, Response>> void execute(final ActionType<Response> action,
                                                                              final Request request, ActionListener<Response> listener) {
        final TransportActionNodeProxy<Request, Response> proxy = proxies.get(action);
        assert proxy != null : "no proxy found for action: " + action;
        nodesService.execute((n, l) -> proxy.execute(n, request, l), listener);
    }
}
