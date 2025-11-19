package com.example;

import java.io.IOException;
import java.util.UUID;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZookeeperClient {
    private RaftClient raftClient;
    public ZookeeperClient(String[] ids , int[] ports, String groupId)
    {
        RaftPeer[] peers = new RaftPeer[ids.length];
        for (int i = 0 ; i < ids.length ; i++)
        {
            peers[i] = RaftPeer.newBuilder().setId(ids[i]).setAddress("localhost:"+String.valueOf(ports[i])).build();
        }
        RaftGroupId raftGroupId = RaftGroupId.valueOf(UUID.fromString(groupId));
        RaftGroup raftGroup = RaftGroup.valueOf(raftGroupId,peers);
        RaftProperties properties = new RaftProperties();
        GrpcFactory grpcFactory = new GrpcFactory(new Parameters());
        try {
            ClientId clientId = ClientId.randomId();
            raftClient = RaftClient.newBuilder()
                .setProperties(properties)
                .setClientId(clientId)
                .setRaftGroup(raftGroup)
                .setClientRpc(grpcFactory.newRaftClientRpc(clientId, properties))
                .build();
        } catch (Exception e) {
        }
    }
    public String readAll()
    {
        String message = "READALL";
        String response = "";
        try {
            RaftClientReply reply = this.raftClient.io().sendReadOnly(Message.valueOf(message));
            if (reply.isSuccess())
            {
                response = reply.getMessage().getContent().toStringUtf8();
            }
        } catch (Exception e) {
            response = "ERROR";
        }
            return response;

    }
    public String read(String key)
    {
        String message = "GET "+key;
        String response = "";
        try {
            RaftClientReply reply = this.raftClient.io().sendReadOnly(Message.valueOf(message));
            if (reply.isSuccess())
            {
                response = reply.getMessage().getContent().toStringUtf8();
            }
        } catch (Exception e) {
            response = "ERROR";
        }
            return response;
    }
    public String write(String key , String value)
    {
        String message = "PUT "+key+"="+value;
        String response = "";
        try {
            RaftClientReply reply = this.raftClient.io().send(Message.valueOf(message));
            if (reply.isSuccess())
            {
                response = reply.getMessage().getContent().toStringUtf8();
            }
        } catch (Exception e) {
            response = "ERROR";
        }
            return response;
    }
    public Boolean delete(String key)
    {
        String message = "DELETE "+key;
        String response = "";
        try {
            RaftClientReply reply = this.raftClient.io().send(Message.valueOf(message));
            if (reply.isSuccess())
            {
                response = reply.getMessage().getContent().toStringUtf8();
            }
        } catch (Exception e) {
            response = "ERROR";
        }
        return response.equals("OK ENTRY DELETED");
    }
}
