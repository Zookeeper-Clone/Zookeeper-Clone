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
    public static class ReadRequest{
        private String key;
        private String directory;

        public String getKey(){return key;}
        public String getDirectory(){return directory;}
    }
    public static class WriteRequest{
        private String key;
        private String value;
        private String directory;

        public String getKey(){return key;}
        public String getValue(){return value;}
        public String getDirectory(){return directory;}
    }
    public static class DeleteRequest extends ReadRequest{}
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
