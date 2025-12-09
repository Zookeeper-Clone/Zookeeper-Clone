package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import webserver.zookeeper.zookeeper_webserver.services.MetricsService;

@RestController
@RequestMapping("/metrics")
public class MetricsController {
    @Autowired
    private MetricsService metricsService;
}
