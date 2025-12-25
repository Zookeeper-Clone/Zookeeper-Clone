package client.zookeeper;


import org.apache.ratis.client.RaftClient;

public class Client {
    public static void main(String[] args) {
        String[] ids ={"n1","n2","n3","n4","n5"};
        int[] ports = {6001,6002,6003,6004,6005};
        String groupId = "00000000-0000-0000-0000-000000000001";
        RaftClient raftClient = new RaftClientBuilder()
                                    .setPeers(ids, ports)
                                    .setGroupId(groupId)
                                    .build();
        ZookeeperClient client = new ZookeeperClient(raftClient, event -> {});
        ZookeeperClient.AuthenticationResult registerResult = client.register("admin@admin.com","adminpass22");
        System.out.println(registerResult.getMessage());
        client.login("admin@admin.com","adminpass22");
        client.addWatch("nasr","nasr");
    }
}
