package client.zookeeper;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;

public class ZookeeperClient {
    private final RaftClient raftClient;

    public ZookeeperClient(RaftClient raftClient) {
        this.raftClient = raftClient;
    }

    private String sendMessage(String command, boolean expectBoolean) {
        try {
            RaftClientReply reply;
            if (command.startsWith("GET") || command.equals("READALL")) {
                reply = raftClient.io().sendReadOnly(Message.valueOf(command));
            } else {
                reply = raftClient.io().send(Message.valueOf(command));
            }

            if (!reply.isSuccess()) {
                return expectBoolean ? null : "ERROR";
            }

            String content = reply.getMessage().getContent().toStringUtf8();
            return expectBoolean ? content : content;
        } catch (Exception e) {
            return expectBoolean ? null : "ERROR";
        }
    }

    public String readAll() {
        return sendMessage("READALL", false);
    }

    public String read(String key) {
        return sendMessage("GET " + key, false);
    }

    public String write(String key, String value) {
        return sendMessage("PUT " + key + "=" + value, false);
    }

    public boolean delete(String key) {
        String result = sendMessage("DELETE " + key, false);
        return "OK ENTRY DELETED".equals(result);
    }
}
