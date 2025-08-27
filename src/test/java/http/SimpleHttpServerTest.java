package http;

import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleHttpServerTest {

    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        SimpleHttpServer.tasks.clear();
    }

    @Test
    void testParseJsonValid() throws Exception {
        Method m = SimpleHttpServer.class.getDeclaredMethod("parseJson", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) m.invoke(null,
                "{\"title\":\"Task\",\"description\":\"Desc\",\"done\":\"true\"}");

        assertEquals("Task", result.get("title"));
        assertEquals("Desc", result.get("description"));
        assertEquals("true", result.get("done"));
    }

    @Test
    void testParseJsonInvalid() throws Exception {
        Method m = SimpleHttpServer.class.getDeclaredMethod("parseJson", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> result = (Map<String, String>) m.invoke(null, "invalid");

        assertTrue(result.isEmpty());
    }

    @Test
    void testHandleApiRequestGetEmpty() throws IOException {
        SimpleHttpServer.handleApiRequest("GET", "/api/tasks",
                new BufferedReader(new StringReader("")), outputStream);

        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("200 OK"));
        assertTrue(response.contains("[]"));
    }

    @Test
    void testHandleApiRequestPostValid() throws IOException {
        String body = "{\"title\":\"T1\",\"description\":\"D1\",\"done\":\"false\"}";
        String request = "POST /api/tasks HTTP/1.1\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;

        SimpleHttpServer.handleApiRequest("POST", "/api/tasks",
                new BufferedReader(new StringReader(request)), outputStream);

        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("201 Created"));
        assertEquals(1, SimpleHttpServer.tasks.size());
    }

    @Test
    void testHandleApiRequestPostMissingFields() throws IOException {
        String body = "{\"title\":\"T2\"}";
        String request = "POST /api/tasks HTTP/1.1\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;

        SimpleHttpServer.handleApiRequest("POST", "/api/tasks",
                new BufferedReader(new StringReader(request)), outputStream);

        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("400 Bad Request"));
        assertTrue(response.contains("Missing fields"));
    }



    @Test
    void testHandleApiRequestUnsupportedMethod() throws IOException {
        SimpleHttpServer.handleApiRequest("PUT", "/api/tasks",
                new BufferedReader(new StringReader("")), outputStream);

        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("405 Method Not Allowed"));
    }

    @Test
    void testServeStaticFileNotFound() throws Exception {
        Method m = SimpleHttpServer.class.getDeclaredMethod("serveStaticFile", String.class, OutputStream.class);
        m.setAccessible(true);

        m.invoke(null, "/no_existe.html", outputStream);
        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("404 Not Found"));
    }

    @Test
    void testServeStaticFileOk() throws Exception {
        File dir = new File("public");
        dir.mkdir();
        File file = new File(dir, "index.html");
        Files.writeString(file.toPath(), "<html>ok</html>");

        Method m = SimpleHttpServer.class.getDeclaredMethod("serveStaticFile", String.class, OutputStream.class);
        m.setAccessible(true);

        m.invoke(null, "/index.html", outputStream);
        String response = outputStream.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("200 OK"));
        assertTrue(response.contains("<html>ok</html>"));

        file.delete();
        dir.delete();
    }

    @Test
    void testGetContentType() throws Exception {
        Method m = SimpleHttpServer.class.getDeclaredMethod("getContentType", String.class);
        m.setAccessible(true);

        assertEquals("text/html", m.invoke(null, "a.html"));
        assertEquals("text/css", m.invoke(null, "a.css"));
        assertEquals("application/javascript", m.invoke(null, "a.js"));
        assertEquals("image/png", m.invoke(null, "a.png"));
        assertEquals("image/jpeg", m.invoke(null, "a.jpg"));
        assertEquals("text/plain", m.invoke(null, "file"));
    }

    @Test
    void testHandleRequestWithRoute() throws Exception {
        SimpleHttpServer.get("/App/test", (req, res) -> "HelloTest");

        String request = "GET /App/test?name=World HTTP/1.1\r\n\r\n";
        InputStream in = new ByteArrayInputStream(request.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Socket fakeSocket = new Socket() {
            @Override public InputStream getInputStream() { return in; }
            @Override public OutputStream getOutputStream() { return out; }
            @Override public void close() {}
        };

        Method m = SimpleHttpServer.class.getDeclaredMethod("handleRequest", Socket.class);
        m.setAccessible(true);
        m.invoke(null, fakeSocket);

        String response = out.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("200 OK"));
        assertTrue(response.contains("HelloTest"));
    }

}
