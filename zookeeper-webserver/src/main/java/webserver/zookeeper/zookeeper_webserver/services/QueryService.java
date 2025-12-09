package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
    private Logger LOG = LoggerFactory.getLogger(QueryService.class);
    @Autowired
    private ZookeeperClient zookeeperClient;

    public String read(String key){
        LOG.info(zookeeperClient.read(key).getValue());
        return zookeeperClient.read(key).getValue();
    }
}
