package webserver.zookeeper.zookeeper_webserver.controllers;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.services.QueryService;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

@RestController
@RequestMapping("/query")
public class QueryController {
    public static abstract class Request{
        private String key;
        private String directory;

        public String getKey(){return key;}
        public String getDirectory(){return directory;}
    }
    public static class WriteRequest extends Request{
        private String value;

        public String getValue(){return value;}
    }
    public static class ReadRequest extends Request{}
    public static class DeleteRequest extends Request{}
    @Autowired
    private QueryService queryService;
    @Autowired
    private ZookeeperService zookeeperService;
    @PostMapping("/read")
    public ResponseEntity<String> read(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody ReadRequest readRequest
    ) {
        System.out.println(sessionToken);
        zookeeperService.setToken(sessionToken);
        return queryService.read(readRequest);
    }

    @PostMapping("/write")
    public ResponseEntity<String> write(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody WriteRequest writeRequest
    ) {
        System.out.println(sessionToken);
        zookeeperService.setToken(sessionToken);
        return queryService.write(writeRequest);
    }

    @PostMapping("/delete")
    public ResponseEntity<String> delete(
            @CookieValue("SESSION_TOKEN") String sessionToken,
            @RequestBody DeleteRequest deleteRequest
    ) {
        System.out.println(sessionToken);
        zookeeperService.setToken(sessionToken);
        return queryService.delete(deleteRequest);
    }
}
