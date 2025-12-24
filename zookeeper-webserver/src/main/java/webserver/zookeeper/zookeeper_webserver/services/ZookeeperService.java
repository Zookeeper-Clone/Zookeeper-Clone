package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ZookeeperService {

    private final ZookeeperClient zookeeperClient;

    @Autowired
    public ZookeeperService(ZookeeperClient zookeeperClient) {
        this.zookeeperClient = zookeeperClient;
    }

    public void setToken(String token){
        zookeeperClient.setSessionToken(token);
    }
}
