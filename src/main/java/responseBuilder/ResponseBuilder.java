package responseBuilder;

import common.StatusCode;

import java.util.HashMap;
import java.util.Map;

public class ResponseBuilder {
    private String httpVersion = "HTTP/1.1";
    private StatusCode statusCode = StatusCode.OK;
    private Map<String, String> headers = new HashMap<>();
    private byte[] body = null;

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setStatusCode(StatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getBody() {
        return body;
    }
}