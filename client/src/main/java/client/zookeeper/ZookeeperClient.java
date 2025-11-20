package client.zookeeper;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;

public class ZookeeperClient {
    private final RaftClient raftClient;

    public ZookeeperClient(RaftClient raftClient) {
        this.raftClient = raftClient;
    }

    public String readAll() {
        try {
            RaftClientReply reply = raftClient.io().sendReadOnly(Message.valueOf("READALL"));
            return reply.isSuccess()
                    ? reply.getMessage().getContent().toStringUtf8()
                    : "ERROR";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public String read(String key) {
        try {
            RaftClientReply reply = raftClient.io().sendReadOnly(Message.valueOf("GET " + key));
            return reply.isSuccess()
                    ? reply.getMessage().getContent().toStringUtf8()
                    : "ERROR";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public String write(String key, String value) {
        try {
            RaftClientReply reply = raftClient.io().send(Message.valueOf("PUT " + key + "=" + value));
            return reply.isSuccess()
                    ? reply.getMessage().getContent().toStringUtf8()
                    : "ERROR";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public boolean delete(String key) {
        try {
            RaftClientReply reply = raftClient.io().send(Message.valueOf("DELETE " + key));
            return reply.isSuccess()
                    && "OK ENTRY DELETED".equals(reply.getMessage().getContent().toStringUtf8());
        } catch (Exception e) {
            return false;
        }
    }
}
