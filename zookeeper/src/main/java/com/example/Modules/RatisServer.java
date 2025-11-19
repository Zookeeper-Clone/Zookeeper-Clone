package com.example.Modules;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RatisServer {
    private final RaftServer server;
    Logger LOG = LoggerFactory.getLogger(RatisServer.class);

    public RatisServer(String nodeId, int port , RaftGroup raftGroup) throws IOException{
        File storageDir = new File("./raft-data" + nodeId);
        RaftProperties properties = new RaftProperties();
        RaftServerConfigKeys.Read.setOption(properties,RaftServerConfigKeys.Read.Option.LINEARIZABLE);
        RaftServerConfigKeys.setStorageDir(properties,Collections.singletonList(storageDir));
        GrpcConfigKeys.Server.setPort(properties, port);
        RaftServerConfigKeys.Snapshot.setAutoTriggerEnabled(properties,true);
        RaftServerConfigKeys.Snapshot.setAutoTriggerThreshold(properties,10);
        this.server = RaftServer.newBuilder()
        .setServerId(RaftPeerId.valueOf(nodeId))
        .setGroup(raftGroup)
        .setProperties(properties)
        .setStateMachine(new RocksStateMachine())
        .build();
    }

    public void start() throws IOException
    {
        server.start();
        LOG.info("Raft Server {} started at port : {} " , server.getId(),server.getServerRpc().getInetSocketAddress());

    }
    public void close() throws IOException
    {
        server.close();
    }
}
