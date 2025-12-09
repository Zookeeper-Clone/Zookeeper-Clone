package webserver.zookeeper.zookeeper_webserver.controllers;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.services.QueryService;

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

    @PostMapping("/read")
    public ResponseEntity<String> read(@RequestBody ReadRequest readRequest){
        return queryService.read(readRequest);
    }
    @PostMapping("/write")
    public ResponseEntity<String> write(@RequestBody WriteRequest writeRequest){
        return queryService.write(writeRequest);
    }
    @PostMapping("/delete")
    public ResponseEntity<String> delete(@RequestBody DeleteRequest deleteRequest){
        return queryService.delete(deleteRequest);
    }
}
