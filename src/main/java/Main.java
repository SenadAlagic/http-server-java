import httpServer.HttpServer;

import java.nio.file.Path;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        Optional<Path> directory = Optional.empty();
        if (args.length > 1 && args[0].equals("--directory")) {
            directory = Optional.of(Path.of(args[1]));
        }
        HttpServer server = new HttpServer(4221, directory);
        server.run();
    }
}
