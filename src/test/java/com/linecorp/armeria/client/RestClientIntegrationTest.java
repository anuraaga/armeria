/*
 * Copyright 2015 LINE Corporation
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

package com.linecorp.armeria.client;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Future;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.linecorp.armeria.client.rest.annotations.Body;
import com.linecorp.armeria.client.rest.annotations.GET;
import com.linecorp.armeria.client.rest.annotations.POST;
import com.linecorp.armeria.client.rest.annotations.Path;
import com.linecorp.armeria.client.rest.annotations.Query;
import com.linecorp.armeria.client.rest.annotations.RestInterface;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.VirtualHostBuilder;
import com.linecorp.armeria.server.http.HttpService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public class RestClientIntegrationTest {

    static class Request {
        public String strVal;
        public int intVal;
    }

    static class Response {
        public String strVal;
        public int intVal;
    }

    @RestInterface
    static interface RestClient {
        @GET("/jsonapi")
        Future<Response> call1();

        @POST("/jsonapi")
        Future<Response> call2(@Body Request request);

        @GET("/jsonapi")
        Future<Response> call3(@Query("strVal") String strVal, @Query("intVal") int intVal);

        @GET("/{path}")
        Future<Response> call4(@Path("path") String path);
    }

    private static final Server server;

    private static int httpPort;
    private static RemoteInvokerFactory remoteInvokerFactory;

    static {
        final SelfSignedCertificate ssc;
        final ServerBuilder sb = new ServerBuilder();

        try {
            ssc = new SelfSignedCertificate("127.0.0.1");

            sb.port(0, SessionProtocol.HTTP);

            VirtualHostBuilder vhBuilder = new VirtualHostBuilder();
            vhBuilder.serviceAt("/jsonapi", new HttpService(
                    (ctx, executor, promise) -> {
                        FullHttpRequest request = ctx.originalRequest();
                        QueryStringDecoder uri = new QueryStringDecoder(request.uri());
                        ByteBuf content = ctx.alloc().ioBuffer();
                        byte[] body = ByteBufUtil.getBytes(request.content());
                        Response responseJson = new Response();
                        if (body.length > 0) {
                            Request testRequest = new ObjectMapper().readValue(body, Request.class);
                            responseJson.strVal = testRequest.strVal;
                            responseJson.intVal = testRequest.intVal;
                        } else if (uri.parameters().containsKey("strVal")) {
                            responseJson.strVal = uri.parameters().get("strVal").get(0);
                            responseJson.intVal = Integer.valueOf(uri.parameters().get("intVal").get(0));
                        } else {
                            responseJson.strVal = "foo";
                            responseJson.intVal = 1;
                        }
                        content.writeBytes(new ObjectMapper().writeValueAsBytes(responseJson));
                        DefaultFullHttpResponse response =
                                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                                            content, false);
                        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "alwayscache");
                        promise.setSuccess(response);
                    }));
            sb.defaultVirtualHost(
                    vhBuilder.sslContext(SessionProtocol.HTTPS, ssc.certificate(), ssc.privateKey()).build());
        } catch (Exception e) {
            throw new Error(e);
        }
        server = sb.build();
    }

    @BeforeClass
    public static void init() throws Exception {
        server.start().sync();
        httpPort = server.activePorts().values().stream()
                .filter(p -> p.protocol() == SessionProtocol.HTTP).findAny().get().localAddress()
                .getPort();
        remoteInvokerFactory = RemoteInvokerFactory.DEFAULT;
    }

    @AfterClass
    public static void destroy() throws Exception {
        remoteInvokerFactory.close();
        server.stop();
    }

    @Test
    public void testCall1() throws Exception {
        RestClient client = Clients.newClient(remoteInvokerFactory, "json+http://127.0.0.1:" + httpPort,
                                              RestClient.class);
        Response response = client.call1().get();
        assertEquals("foo", response.strVal);
        assertEquals(1, response.intVal);
    }

    @Test
    public void testCall2() throws Exception {
        RestClient client = Clients.newClient(remoteInvokerFactory, "json+http://127.0.0.1:" + httpPort,
                                              RestClient.class);
        Request request = new Request();
        request.strVal = "bar";
        request.intVal = 3;
        Response response = client.call2(request).get();
        assertEquals("bar", response.strVal);
        assertEquals(3, response.intVal);
    }

    @Test
    public void testCall3() throws Exception {
        RestClient client = Clients.newClient(remoteInvokerFactory, "json+http://127.0.0.1:" + httpPort,
                                              RestClient.class);
        Response response = client.call3("cat", 4).get();
        assertEquals("cat", response.strVal);
        assertEquals(4, response.intVal);
    }

    @Test
    public void testCall4() throws Exception {
        RestClient client = Clients.newClient(remoteInvokerFactory, "json+http://127.0.0.1:" + httpPort,
                                              RestClient.class);
        Response response = client.call4("jsonapi").get();
        assertEquals("foo", response.strVal);
        assertEquals(1, response.intVal);
    }
}
