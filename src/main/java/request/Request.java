package request;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Request {
    public String method;
    public String route;
    public String httpVersion;
    public String params;
    public Map<String, String> headers;
    public byte[] body;

    public Request() {
        this.method = "";
        this.route = "";
        this.httpVersion = "";
        this.params = "";
        this.headers = new HashMap<>();
        this.body = new byte[0];
    }

    public Request(String method, String route, String params, String httpVersion, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.route = route;
        this.params = params;
        this.httpVersion = httpVersion;
        this.headers = headers;
        this.body = body;
    }

    public static Request readRequest(InputStream inputStream) {
        try {
            ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1];
            int bytesRead;
            boolean foundHeaderEnd = false;
            int consecutiveNewlines = 0;

            // Read headers byte by byte until we find the header/body separator (\r\n\r\n)
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                headerBytes.write(buffer, 0, bytesRead);

                // Check for header end sequence
                if (buffer[0] == '\r' || buffer[0] == '\n') {
                    consecutiveNewlines++;
                } else {
                    consecutiveNewlines = 0;
                }

                // We found \r\n\r\n (or \n\n in some cases)
                if (consecutiveNewlines == 4) {
                    foundHeaderEnd = true;
                    break;
                }
            }

            if (!foundHeaderEnd) {
                return new Request(); // Malformed request
            }

            // Process headers
            String headerString = headerBytes.toString(StandardCharsets.UTF_8);
            String[] headerLines = headerString.split("\r\n|\n\n|\r\r|\n");

            // First line is the request line
            String requestLine = headerLines[0];
            String[] requestInfo = requestLine.split(" ", 3);
            if (requestInfo.length < 3) return new Request();

            String method = requestInfo[0];
            String route = requestInfo[1];
            String httpVersion = requestInfo[2];
            String params = "";

            // Separate route from parameters
            int lastSlashIndex = route.lastIndexOf('/');
            if (lastSlashIndex != 0) {
                params = route.substring(lastSlashIndex + 1);
                route = route.substring(0, lastSlashIndex);
            }

            // Process the headers
            Map<String, String> headers = getHeaders(headerLines);

            // Process the body
            byte[] bodyBytes = getBody(inputStream, headers);

            return new Request(method, route, params, httpVersion, headers, bodyBytes);
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            return new Request();
        }
    }

   public static Map<String, String> getHeaders(String[] headerLines) {
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i];
            if (!line.isEmpty()) {
                String[] pair = line.split(": ", 2);
                if (pair.length == 2) {
                    headers.put(pair[0], pair[1]);
                }
            }
        }
        return headers;
    }

    public static byte[] getBody(InputStream inputStream, Map<String,String> headers) throws IOException {
        byte[] bodyBytes = new byte[0];
        if (headers.containsKey("Content-Length")) {
            int contentLength = Integer.parseInt(headers.get("Content-Length"));
            if (contentLength > 0) {
                ByteArrayOutputStream bodyStream = new ByteArrayOutputStream(contentLength);
                byte[] bodyBuffer = new byte[8192]; // 8KB buffer
                int totalBytesRead = 0;
                int bodyBytesRead;

                while (totalBytesRead < contentLength &&
                        (bodyBytesRead = inputStream.read(bodyBuffer, 0,
                                Math.min(bodyBuffer.length, contentLength - totalBytesRead))) != -1) {
                    bodyStream.write(bodyBuffer, 0, bodyBytesRead);
                    totalBytesRead += bodyBytesRead;
                }

                bodyBytes = bodyStream.toByteArray();
            }
        }
        return bodyBytes;
    }
}
