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
        return req.getIsUpdate()
                ? handleUpdate(req)
                : handleCreate(req);
    }

    private ResponseEntity<String> handleUpdate(QueryController.WriteRequest req) {
        return req.getDirectory() == null
                ? handleQueryResult(zookeeperClient.update(req.getKey(), req.getValue()))
                : handleQueryResult(zookeeperClient.update(req.getKey(), req.getValue(), req.getDirectory()));
    }

    private ResponseEntity<String> handleCreate(QueryController.WriteRequest req) {
        return req.getDirectory() == null
                ? handleQueryResult(zookeeperClient.create(req.getKey(), req.getValue(), req.getIsEphemeral()))
                : handleQueryResult(zookeeperClient.create(req.getKey(), req.getValue(), req.getDirectory(), req.getIsEphemeral()));
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