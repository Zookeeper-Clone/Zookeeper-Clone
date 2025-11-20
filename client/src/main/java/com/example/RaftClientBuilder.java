package com.example;

import java.util.UUID;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;

public class RaftClientBuilder {

    private String[] ids;
    private int[] ports;
    private String groupId;

    public RaftClientBuilder setPeers(String[] ids, int[] ports) {
        this.ids = ids;
        this.ports = ports;
        return this;
    }

    public RaftClientBuilder setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public RaftClient build(){
        // Build peers array
        RaftPeer[] peers = new RaftPeer[ids.length];
        for (int i = 0; i < ids.length; i++) {
            peers[i] = RaftPeer.newBuilder()
                    .setId(ids[i])
                    .setAddress("localhost:" + ports[i])
                    .build();
        }

        // Build group
        RaftGroupId raftGroupId = RaftGroupId.valueOf(UUID.fromString(groupId));
        RaftGroup raftGroup = RaftGroup.valueOf(raftGroupId, peers);

        // Properties + RPC
        RaftProperties properties = new RaftProperties();
        ClientId clientId = ClientId.randomId();
        GrpcFactory grpcFactory = new GrpcFactory(new Parameters());

        // Build RaftClient
        return RaftClient.newBuilder()
                .setClientId(clientId)
                .setRaftGroup(raftGroup)
                .setProperties(properties)
                .setClientRpc(grpcFactory.newRaftClientRpc(clientId, properties))
                .build();
    }
}
