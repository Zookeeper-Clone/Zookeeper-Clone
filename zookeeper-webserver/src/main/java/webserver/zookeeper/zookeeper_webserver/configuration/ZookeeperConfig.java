package webserver.zookeeper.zookeeper_webserver.configuration;

import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;
import org.apache.ratis.client.RaftClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZookeeperConfig {
    @Bean
    public RaftClient raftClient(){
        String[] ids = {"n1","n2","n3","n4","n5"};
        int[] ports = {6001,6002,6003,6004,6005};
        String groupId = "00000000-0000-0000-0000-000000000001";
        return new RaftClientBuilder()
                .setPeers(ids,ports)
                .setGroupId(groupId)
                .build();
    }
    @Bean
    public ZookeeperClient zookeeperClient(RaftClient raftClient){
        return new ZookeeperClient(raftClient, event -> {});
    }
}
