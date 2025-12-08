package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
    @Autowired
    private ZookeeperClient zookeeperClient;

    public String read(String key){
        return zookeeperClient.read(key).getValue();
    }
}
