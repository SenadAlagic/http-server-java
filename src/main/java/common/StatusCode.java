package common;

public enum StatusCode {
    OK(200, "200 OK"),
    CREATED(201, "201 Created"),
    NOT_FOUND(404, "404 Not Found"),
    INTERNAL_SERVER_ERROR(500, "500 Internal Server Error");

    private final int code;
    private final String message;

    StatusCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}