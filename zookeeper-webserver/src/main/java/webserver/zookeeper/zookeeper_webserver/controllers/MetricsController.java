package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import webserver.zookeeper.zookeeper_webserver.services.MetricsService;

import java.util.Map;

@RestController
public class MetricsController {

    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics/ratis")
    public Map<String, Object> getMetrics() {
        return service.collectMetrics();
    }
}