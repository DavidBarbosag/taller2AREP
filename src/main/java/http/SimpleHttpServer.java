package http;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servidor HTTP en Java sin frameworks.
 * - Atiende múltiples solicitudes secuenciales (no concurrentes).
 * - Sirve archivos estáticos desde la carpeta "public/".
 * - Expone una API REST simple en "/api/tasks" con GET y POST.
 */
public class SimpleHttpServer {

    public static final List<Task> tasks = new ArrayList<>();
    private static final Map<String, RouteHandler> getRoutes = new HashMap<>();
    private static String staticFilesFolder = "public";

    @FunctionalInterface
    public interface RouteHandler {
        String handle(Request req, Response res);
    }

    public static class Request {
        private final String path;
        private final Map<String, String> queryParams;
        public Request(String path, Map<String, String> queryParams) {
            this.path = path;
            this.queryParams = queryParams;
        }
        public String getPath() { return path; }
        public String getValues(String key) { return queryParams.get(key); }
    }

    public static class Response {
    }

    public static void get(String path, RouteHandler handler) {
        getRoutes.put(path, handler);
    }

    public static void staticfiles(String folder) {
        staticFilesFolder = folder;
    }


    public static void main(String[] args) {

        staticfiles("/public");
        get("/App/hello", (req, res) -> "Hello " + req.getValues("name"));
        get("/App/pi", (req, res) -> String.valueOf(Math.PI));

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(35000);
            System.out.println("Servidor iniciado en el puerto 35000");
        } catch (IOException e) {
            System.err.println("No se pudo escuchar en el puerto 35000.");
            System.exit(1);
        }

        boolean running = true;
        while (running) {
            try (Socket clientSocket = serverSocket.accept()) {
                handleRequest(clientSocket);
            } catch (IOException e) {
                System.err.println("Error al aceptar conexión: " + e.getMessage());
            }
        }
    }

    /**
     * Maneja una solicitud HTTP de un cliente.
     * @param clientSocket El socket del cliente conectado.
     * @throws IOException Si ocurre un error al leer o escribir en el socket.
     */
    private static void handleRequest(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        OutputStream out = clientSocket.getOutputStream();

        String requestLine = in.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) return;

        System.out.println("Solicitud: " + requestLine);

        String[] requestParts = requestLine.split(" ");
        String method = requestParts[0];
        String fullPath = requestParts[1];


        String path = fullPath.split("\\?")[0];
        Map<String, String> queryParams = new HashMap<>();
        if (fullPath.contains("?")) {
            String query = fullPath.substring(fullPath.indexOf('?') + 1);
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2) queryParams.put(kv[0], kv[1]);
            }
        }

        boolean handled = false;

        if (method.equals("GET") && getRoutes.containsKey(path)) {
            String body = getRoutes.get(path).handle(new Request(path, queryParams), new Response());
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n" + body;
            out.write(response.getBytes());
            handled = true;
        } else if (path.startsWith("/api/tasks")) {
            handleApiRequest(method, path, in, out);
            handled = true;
        }

        if (!handled) {
            serveStaticFile(path, out);
        }

        in.close();
        out.close();
        clientSocket.close();
    }

    /**
     * Maneja solicitudes API para obtener o agregar tareas (GET y POST).
     * @param method El método HTTP (GET o POST).
     * @param path La ruta de la solicitud (debería ser "/api/tasks").
     * @param in El BufferedReader para leer el cuerpo de la solicitud.
     * @param out El OutputStream para enviar la respuesta.
     * @throws IOException Si ocurre un error al leer o escribir en el socket.
     */
    static void handleApiRequest(String method, String path, BufferedReader in, OutputStream out) throws IOException {
        String response;

        if (method.equals("GET")) {
            StringBuilder jsonResponse = new StringBuilder("[");
            for (int i = 0; i < tasks.size(); i++) {
                Task t = tasks.get(i);
                jsonResponse.append("{\"title\":\"").append(t.getTitle())
                        .append("\", \"description\":\"").append(t.getDescription())
                        .append("\", \"done\":").append(t.isDone()).append("}");
                if (i < tasks.size() - 1) {
                    jsonResponse.append(",");
                }
            }
            jsonResponse.append("]");

            response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + jsonResponse;

        } else if (method.equals("POST")) {
            String body = readRequestBody(in);
            System.out.println("Cuerpo recibido: " + body);

            try {
                Map<String, String> data = parseJson(body);
                if (data.containsKey("title") && data.containsKey("description") && data.containsKey("done")) {
                    tasks.add(new Task(
                            data.get("title"),
                            data.get("description"),
                            Boolean.parseBoolean(data.get("done"))
                    ));
                    response = "HTTP/1.1 201 Created\r\nContent-Type: text/plain\r\n\r\nTask added";
                } else {
                    response = "HTTP/1.1 400 Bad Request\r\n\r\nMissing fields";
                }
            } catch (Exception e) {
                response = "HTTP/1.1 400 Bad Request\r\n\r\nInvalid JSON format";
            }

        } else {
            response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
        }

        out.write(response.getBytes());
        out.flush();
    }

    /**
     * Lee el cuerpo de una solicitud HTTP POST.
     * @param in El BufferedReader para leer la solicitud.
     * @throws IOException Si ocurre un error al leer del BufferedReader.
     */
    private static String readRequestBody(BufferedReader in) throws IOException {
        StringBuilder body = new StringBuilder();
        int contentLength = 0;

        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.substring(15).trim());
            }
        }

        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            in.read(buffer, 0, contentLength);
            body.append(buffer);
        }

        return body.toString();
    }

    /**
     * Parsea un JSON muy simple en un mapa clave-valor.
     * @param json El string JSON a parsear.
     */
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    /**
     * Sirve archivos estáticos desde la carpeta "public/".
     * Si el archivo no existe, devuelve un error 404.
     * @param path La ruta del archivo solicitado.
     * @param out El OutputStream para enviar la respuesta.
     * @throws IOException Si ocurre un error al leer o escribir en el socket.
     */
    private static void serveStaticFile(String path, OutputStream out) throws IOException {
        if (path.equals("/")) path = "/index.html";

        File file = new File("public" + path);
        if (file.exists() && !file.isDirectory()) {
            String contentType = getContentType(path);
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes());
            out.write(fileBytes);
        } else {
            out.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
        }
        out.flush();
    }

    /**
     * Determina el tipo de contenido basado en la extensión del archivo.
     * @param path La ruta del archivo.
     * @return El tipo de contenido correspondiente.
     */
    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        return "text/plain";
    }
}
