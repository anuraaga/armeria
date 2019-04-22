/*
 * Copyright 2019 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.common.grpc.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import org.junit.ClassRule;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.grpc.testing.Messages.Payload;
import com.linecorp.armeria.grpc.testing.Messages.SimpleRequest;
import com.linecorp.armeria.grpc.testing.Messages.SimpleResponse;
import com.linecorp.armeria.grpc.testing.TestServiceGrpc.TestServiceBlockingStub;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.testing.server.ServerRule;

public class AbstractUnaryGrpcServiceTest {

    // This service only depends on protobuf. Users can use a custom decoder / encoder to avoid even that.
    private static class TestService extends AbstractUnaryGrpcService {

        @Override
        protected CompletableFuture<byte[]> handleMessage(byte[] message) {
            final SimpleRequest request;
            try {
                request = SimpleRequest.parseFrom(message);
            } catch (InvalidProtocolBufferException e) {
                throw new UncheckedIOException(e);
            }
            SimpleResponse response = SimpleResponse.newBuilder()
                                                    .setPayload(request.getPayload())
                                                    .build();
            return CompletableFuture.completedFuture(response.toByteArray());
        }
    }

    @ClassRule
    public static ServerRule server = new ServerRule() {
        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/armeria.grpc.testing.TestService/UnaryCall", new TestService());
        }
    };

    @Test
    public void normal() {
        TestServiceBlockingStub stub =
                Clients.newClient(server.httpUri(GrpcSerializationFormats.PROTO, "/"),
                                  TestServiceBlockingStub.class);
        assertThat(stub.unaryCall(SimpleRequest.newBuilder()
                                               .setPayload(Payload.newBuilder()
                                                                  .setBody(
                                                                          ByteString.copyFromUtf8("hello"))
                                                                  .build())
                                               .build()).getPayload().getBody().toStringUtf8())
                  .isEqualTo("hello");
    }

    @Test
    public void invalidPayload() {
        HttpClient client = HttpClient.of(server.httpUri("/"));

        AggregatedHttpMessage message =
                client.post("/armeria.grpc.testing.TestService/UnaryCall", "foobarbreak").aggregate().join();

        assertThat(message.headers().status()).isEqualTo(HttpStatus.OK);
        assertThat(message.headers().get(GrpcHeaderNames.GRPC_STATUS))
                .isEqualTo(Integer.toString(StatusCodes.INTERNAL));
        assertThat(message.headers().get(GrpcHeaderNames.GRPC_MESSAGE)).isNotBlank();
    }
}
