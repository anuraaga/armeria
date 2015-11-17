package com.linecorp.armeria.client.thrift;

import static org.junit.Assert.assertEquals;

import org.apache.thrift.server.TServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.common.thrift.ThriftProtocolFactories;
import com.linecorp.armeria.service.test.thrift.main.HelloService;

/**
 * Test to verify interaction between armeria client and official thrift
 * library's {@link TServlet}.
 */
public class ThriftOverHttpClientTServletIntegrationTest {

    private static final HelloService.Iface helloHandler = name -> "Hello, " + name + '!';

    private static Server server;

    @BeforeClass
    public static void createServer() throws Exception {
        HelloService.Processor processor = new HelloService.Processor(helloHandler);
        TServlet servlet = new TServlet(processor, ThriftProtocolFactories.BINARY);
        server = new Server(0);
        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(new ServletHolder(servlet), "/thrift");
        server.setHandler(handler);
        server.start();
    }

    @AfterClass
    public static void destroyServer() throws Exception {
        server.stop();
    }

    @Test
    public void sendHello() throws Exception {
        HelloService.Iface client = Clients.newClient(getUrl("/thrift"), HelloService.Iface.class);
        assertEquals("Hello, world!", client.hello("world"));
    }

    private String getUrl(String path) {
        int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
        return "tbinary+http://localhost:" + port + path;
    }
}
