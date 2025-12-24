package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import webserver.zookeeper.zookeeper_webserver.controllers.QueryController;

@Service
public class QueryService {

    private final ZookeeperClient zookeeperClient;

    @Autowired
    public QueryService(ZookeeperClient zookeeperClient) {
        this.zookeeperClient = zookeeperClient;
    }

    public ResponseEntity<String> read(QueryController.ReadRequest req) {
        return handleQueryResult(
                req.getDirectory() == null ? zookeeperClient.read(req.getKey())
                        : zookeeperClient.read(req.getKey(), req.getDirectory()));
    }

    public ResponseEntity<String> write(QueryController.WriteRequest req) {
        return handleQueryResult(
                req.getDirectory() == null ? zookeeperClient.write(req.getKey(), req.getValue(), req.getIsEphemeral())
                        : zookeeperClient.write(req.getKey(), req.getValue(), req.getDirectory(),
                                req.getIsEphemeral()));
    }

    public ResponseEntity<String> delete(QueryController.DeleteRequest req) {
        return handleQueryResult(
                req.getDirectory() == null ? zookeeperClient.delete(req.getKey())
                        : zookeeperClient.delete(req.getKey(), req.getDirectory()));
    }

    private static ResponseEntity<String> handleQueryResult(ZookeeperClient.QueryResult result) {
        return result.isSuccess() ? ResponseEntity.ok(result.getValue())
                : ResponseEntity.badRequest().body(result.getMessage());
    }
}