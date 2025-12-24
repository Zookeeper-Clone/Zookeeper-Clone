package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.services.MetricsService;
import webserver.zookeeper.zookeeper_webserver.services.QueryService;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/query")
public class QueryController {

    @Autowired
    private QueryService queryService;
    @Autowired
    private ZookeeperService zookeeperService;
    @Autowired
    private MetricsService metricsService;

    public static abstract class Request {
        private String key;
        private String directory;

        public String getKey() {
            return key;
        }

        public String getDirectory() {
            return directory;
        }
    }

    public static class WriteRequest extends Request {
        private String value;
        private boolean isEphemeral;

        public String getValue() {
            return value;
        }

        public boolean getIsEphemeral() {
            return isEphemeral;
        }
    }

    public static class ReadRequest extends Request {
    }

    public static class DeleteRequest extends Request {
    }

    @PostMapping("/read")
    public ResponseEntity<String> read(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody ReadRequest readRequest) throws Exception {
        return handleRequest(() -> queryService.read(readRequest), sessionToken);
    }

    @PostMapping("/write")
    public ResponseEntity<String> write(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody WriteRequest writeRequest) throws Exception {
        return handleRequest(() -> queryService.write(writeRequest), sessionToken);
    }

    @PostMapping("/delete")
    public ResponseEntity<String> delete(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody DeleteRequest deleteRequest) throws Exception {
        return handleRequest(() -> queryService.delete(deleteRequest), sessionToken);
    }

    // --- Helper method to track metrics ---
    private ResponseEntity<String> handleRequest(SupplierWithException<ResponseEntity<String>> action, String token)
            throws Exception {
        zookeeperService.setToken(token);
        long start = System.nanoTime();
        metricsService.recordRequest();
        try {
            ResponseEntity<String> res = action.get();
            if (res.getStatusCode().is2xxSuccessful())
                metricsService.recordSuccess();
            else
                metricsService.recordFail();
            return res;
        } catch (Exception e) {
            metricsService.recordFail();
            throw e;
        } finally {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            metricsService.recordLatency(latencyMs);
        }
    }

    @FunctionalInterface
    interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
