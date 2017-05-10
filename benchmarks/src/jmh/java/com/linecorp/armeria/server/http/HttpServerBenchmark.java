/*
 * Copyright 2017 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.http;

import java.nio.charset.StandardCharsets;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.http.HttpClient;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.http.HttpData;
import com.linecorp.armeria.common.http.HttpRequest;
import com.linecorp.armeria.common.http.HttpResponseWriter;
import com.linecorp.armeria.common.http.HttpSessionProtocols;
import com.linecorp.armeria.common.http.HttpStatus;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServerPort;
import com.linecorp.armeria.server.ServiceRequestContext;

import joptsimple.internal.Strings;

@State(Scope.Benchmark)
public class HttpServerBenchmark {

    private static final byte[] RESPONSE = Strings.repeat('a', 6 * 1024).getBytes(StandardCharsets.UTF_8);

    private Server server;
    private HttpClient client;

    @Setup
    public void setUp() {
        server = new ServerBuilder()
        .port(0, HttpSessionProtocols.HTTP)
        .serviceAt("/http", new AbstractHttpService() {
            @Override
            protected void doGet(ServiceRequestContext ctx, HttpRequest req, HttpResponseWriter res)
                    throws Exception {
                res.respond(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, HttpData.of(RESPONSE));
            }
        })
        .build();
        server.start().join();

        ServerPort httpPort = server.activePorts().values().stream()
                                    .filter(p1 -> p1.protocol() == HttpSessionProtocols.HTTP).findAny()
                                    .get();
        client = Clients.newClient("none+h2c://127.0.0.1:" + httpPort.localAddress().getPort() + "/http",
                                   HttpClient.class);
    }

    @TearDown
    public void tearDown() {
        server.stop().join();
    }

    @Benchmark
    public void normal(Blackhole bh) {
        bh.consume(client.get("/http"));
    }
}
