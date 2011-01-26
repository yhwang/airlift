package com.proofpoint.platform.sample;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jersey.JaxrsModule;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.common.collect.Sets.newHashSet;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestServer
{
    private static final int NOT_ALLOWED = 405;

    private AsyncHttpClient client;
    private TestingHttpServer server;

    private PersonStore store;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        // TODO: wrap all this stuff in a TestBootstrap class
        Injector injector = Guice.createInjector(new TestingHttpServerModule(),
                new JaxrsModule(),
                new MainModule(),
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())));

        server = injector.getInstance(TestingHttpServer.class);
        store = injector.getInstance(PersonStore.class);

        server.start();
        client = new AsyncHttpClient();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
        }

        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testEmpty()
            throws Exception
    {
        Response response = client.prepareGet(urlFor("/v1/person")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);
        assertEquals(fromJson(response.getResponseBody(), List.class), fromJson("[]", List.class));
    }

    @Test
    public void testGetAll()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("bar", new Person("bar@example.com", "Mr Bar"));
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareGet(urlFor("/v1/person")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        List<?> expected = fromJson(Resources.toString(Resources.getResource("list.json"), Charsets.UTF_8), List.class);
        List<?> actual = fromJson(response.getResponseBody(), List.class);

        assertEquals(newHashSet(actual), newHashSet(expected));
    }

    @Test
    public void testGetSingle()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareGet(urlFor("/v1/person/foo")).execute().get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
        assertEquals(response.getContentType(), MediaType.APPLICATION_JSON);

        Map<?, ?> expected = fromJson(Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8), Map.class);
        Map<?, ?> actual = fromJson(response.getResponseBody(), Map.class);

        assertEquals(actual, expected);
    }

    @Test
    public void testPut()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        Response response = client.preparePut(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBody(json)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        assertEquals(store.get("foo"), new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testDelete()
            throws IOException, ExecutionException, InterruptedException
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = client.prepareDelete(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());

        assertNull(store.get("foo"));
    }

    @Test
    public void testDeleteMissing()
            throws IOException, ExecutionException, InterruptedException
    {
        Response response = client.prepareDelete(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), javax.ws.rs.core.Response.Status.OK.getStatusCode());
    }

    @Test
    public void testPostNotAllowed()
            throws IOException, ExecutionException, InterruptedException
    {
        String json = Resources.toString(Resources.getResource("single.json"), Charsets.UTF_8);
        Response response = client.preparePost(urlFor("/v1/person/foo"))
                .setHeader("Content-Type", MediaType.APPLICATION_JSON)
                .setBody(json)
                .execute()
                .get();

        assertEquals(response.getStatusCode(), NOT_ALLOWED);

        assertNull(store.get("foo"));
    }

    private String urlFor(String path)
    {
        return server.getBaseUrl().resolve(path).toString();
    }

    private static <T> T fromJson(String json, Class<T> clazz)
            throws IOException
    {
        // TODO: use JsonCodec or similar
        ObjectMapper mapper = new ObjectMapper();
        return clazz.cast(mapper.readValue(json, Object.class));
    }
}