package httpServer;

import common.Method;
import common.StatusCode;
import request.Request;
import responseBuilder.ResponseBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

public class HttpServer {
    public ExecutorService threadPool;
    private final int port;
    private final Optional<Path> rootDirectory;

    public HttpServer(int port, Optional<Path> rootDirectory) {
        int POOL_SIZE = 10;
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(POOL_SIZE);
        this.rootDirectory = rootDirectory;
    }

    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket socket = serverSocket.accept(); // Wait for connection from client.
                threadPool.submit(() -> handleRequest(socket));
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private void handleRequest(Socket socket) {
        try {
            socket.setSoTimeout(30000); // 30 second timeout for idle connections
            boolean keepAlive = true;

            while (keepAlive) {
                try {
                    // Read and parse the request
                    Request request = Request.readRequest(socket.getInputStream());

                    // Check if we received a valid request
                    if (request.method == null || request.method.isEmpty()) {
                        break;
                    }

                    // Process the request and get response components
                    ResponseBuilder responseBuilder = processRequest(request, rootDirectory);

                    // Determine if connection should be kept alive
                    keepAlive = shouldKeepAlive(request);

                    // Add connection header
                    if (keepAlive) {
                        responseBuilder.addHeader("Connection", "keep-alive");
                    } else {
                        responseBuilder.addHeader("Connection", "close");
                    }

                    // Handle compression if needed
                    handleCompression(request, responseBuilder);

                    // Send the response
                    sendResponse(socket, responseBuilder);

                } catch (IOException e) {
                    System.out.println("Error processing request: " + e.getMessage());
                    keepAlive = false;
                }
            }

            // Close the socket when we're done with all requests or if there was an error
            socket.close();

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    private boolean shouldKeepAlive(Request request) {
        String connectionHeader = request.headers.getOrDefault("Connection", "");
        boolean isHttp11 = request.httpVersion.equals("HTTP/1.1");

        // HTTP/1.1 defaults to keep-alive unless explicitly set to close
        // HTTP/1.0 defaults to close unless explicitly set to keep-alive
        if (isHttp11) {
            return !connectionHeader.equalsIgnoreCase("close");
        } else {
            return connectionHeader.equalsIgnoreCase("keep-alive");
        }
    }

    private ResponseBuilder processRequest(Request request, Optional<Path> rootDirectory) {
        ResponseBuilder builder = new ResponseBuilder();
        builder.setHttpVersion(request.httpVersion);

        if (!allowedRoutes.contains(request.route)) {
            builder.setStatusCode(StatusCode.NOT_FOUND);
        } else if (request.route.startsWith("/echo")) {
            builder.setStatusCode(StatusCode.OK);
            builder.addHeader("Content-Type", "text/plain");
            builder.setBody(request.params.getBytes());
        } else if (request.route.startsWith("/user-agent")) {
            String userAgentHeader = request.headers.get("User-Agent");
            builder.setStatusCode(StatusCode.OK);
            builder.addHeader("Content-Type", "text/plain");
            builder.setBody(userAgentHeader.getBytes());
        } else if (request.route.startsWith("/files") && rootDirectory.isPresent()) {
            processFileRequest(request, rootDirectory.get(), builder);
        } else {
            builder.setStatusCode(StatusCode.OK);
        }
        return builder;
    }

    private void processFileRequest(Request request, Path rootDir, ResponseBuilder builder) {
        if (request.method.equals(Method.GET.toString())) {
            Path filePath = rootDir.resolve(request.params);
            if (Files.exists(filePath)) {
                try {
                    byte[] fileContent = Files.readAllBytes(filePath);
                    builder.setStatusCode(StatusCode.OK);
                    builder.addHeader("Content-Type", "application/octet-stream");
                    builder.setBody(fileContent);
                } catch (IOException e) {
                    builder.setStatusCode(StatusCode.INTERNAL_SERVER_ERROR);
                }
            } else {
                builder.setStatusCode(StatusCode.NOT_FOUND);
            }
        } else if (request.method.equals(Method.POST.toString())) {
            try {
                Path filePath = rootDir.resolve(request.params);
                Files.write(filePath, request.body);
                builder.setStatusCode(StatusCode.CREATED);
            } catch (IOException e) {
                builder.setStatusCode(StatusCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void handleCompression(Request request, ResponseBuilder builder) {
        if (builder.getBody() == null) return;

        if (request.headers.containsKey("Accept-Encoding")) {
            String[] compressions = request.headers.get("Accept-Encoding").split(",");
            for (String compression : compressions) {
                String trimmedCompression = compression.trim();
                if (allowedCompressions.contains(trimmedCompression)) {
                    if ("gzip".equals(trimmedCompression)) {
                        compressWithGzip(builder);
                    }
                    break;
                }
            }
        }
    }

    private void compressWithGzip(ResponseBuilder builder) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(baos)) {
            gzipOutputStream.write(builder.getBody());
            gzipOutputStream.finish();
            gzipOutputStream.flush();
            builder.setBody(baos.toByteArray());
            builder.addHeader("Content-Encoding", "gzip");
        } catch (IOException e) {
            // If compression fails, keep the original body
            System.out.println("Compression failed: " + e.getMessage());
        }
    }

    private void sendResponse(Socket socket, ResponseBuilder builder) throws IOException {
        StringBuilder responseStr = new StringBuilder();
        responseStr.append(builder.getHttpVersion()).append(" ")
                .append(builder.getStatusCode().getMessage()).append("\r\n");

        // Add all headers
        for (Map.Entry<String, String> header : builder.getHeaders().entrySet()) {
            responseStr.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }

        // Add content length if body exists
        byte[] body = builder.getBody();
        if (body != null) {
            // Only add Content-Length if not already added by the builder
            if (!builder.getHeaders().containsKey("Content-Length")) {
                responseStr.append("Content-Length: ").append(body.length).append("\r\n");
            }
        }

        // End headers section
        responseStr.append("\r\n");

        // Send headers
        socket.getOutputStream().write(responseStr.toString().getBytes());
        socket.getOutputStream().flush();

        // Send body if it exists
        if (body != null) {
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
        }
    }

    public static List<String> allowedRoutes = List.of("/", "/echo", "/user-agent", "/files");
    public static List<String> allowedCompressions = List.of("*", "gzip");

}
