import java.io.IOException;
import java.util.HashMap;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.client.RaftClientRpc;
import org.apache.ratis.client.api.AdminApi;
import org.apache.ratis.client.api.AsyncApi;
import org.apache.ratis.client.api.BlockingApi;
import org.apache.ratis.client.api.DataStreamApi;
import org.apache.ratis.client.api.GroupManagementApi;
import org.apache.ratis.client.api.LeaderElectionManagementApi;
import org.apache.ratis.client.api.MessageStreamApi;
import org.apache.ratis.client.api.SnapshotManagementApi;
import org.apache.ratis.proto.RaftProtos.ReplicationLevel;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeerId;

public class MockRaftClient implements RaftClient {

    private final HashMap<String, String> store = new HashMap<>();

    private final BlockingApi blockingApi = new BlockingApi() {
        @Override
        public RaftClientReply send(Message message) {
            return applyMutation(message.getContent().toStringUtf8());
        }

        @Override
        public RaftClientReply sendReadOnly(Message message) {
            return applyQuery(message.getContent().toStringUtf8());
        }

        @Override
        public RaftClientReply sendReadOnly(Message message, RaftPeerId server) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'sendReadOnly'");
        }

        @Override
        public RaftClientReply sendReadOnlyNonLinearizable(Message message) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'sendReadOnlyNonLinearizable'");
        }

        @Override
        public RaftClientReply sendReadAfterWrite(Message message) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'sendReadAfterWrite'");
        }

        @Override
        public RaftClientReply sendStaleRead(Message message, long minIndex, RaftPeerId server) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'sendStaleRead'");
        }

        @Override
        public RaftClientReply watch(long index, ReplicationLevel replication) throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'watch'");
        }
    };

    // ---------------------
    // Core logic
    // ---------------------
    private RaftClientReply applyMutation(String msg) {
        String response;

        if (msg.startsWith("PUT ")) {
            String payload = msg.substring(4);
            String[] parts = payload.split("=", 2);

            if (parts.length != 2) {
                response = "ERROR INVALID MESSAGE";
            } else {
                String key = parts[0];
                String value = parts[1];

                boolean existed = store.containsKey(key);
                store.put(key, value);

                response = existed ? "OK ENTRY UPDATED" : "OK ENTRY ADDED";
            }
        } else if (msg.startsWith("DELETE ")) {
            String key = msg.substring(7).trim();
            response = store.remove(key) != null
                    ? "OK ENTRY DELETED"
                    : "ERROR CAN'T DELETE";
        } else {
            response = "INVALID QUERY";
        }

        return success(response);
    }

    private RaftClientReply applyQuery(String msg) {
        String response;

        if (msg.equals("READALL")) {
            StringBuilder sb = new StringBuilder();

            for (String key : store.keySet()) {
                sb.append(key)
                        .append(" : ")
                        .append(store.get(key))
                        .append("\n");
            }

            response = sb.toString();
        } else if (msg.startsWith("GET ")) {
            String key = msg.substring(4).trim();
            response = store.getOrDefault(key, "KEY DOESN'T EXIST");
        } else {
            response = "INVALID QUERY";
        }

        return success(response);
    }

    private RaftClientReply success(String content) {
        return RaftClientReply.newBuilder()
                .setClientId(ClientId.randomId())
                .setServerId(RaftPeerId.valueOf("mock"))
                .setGroupId(RaftGroupId.randomId())
                .setCallId(1L)
                .setSuccess()
                .setMessage(Message.valueOf(content))
                .setLogIndex(1L)
                .build();
    }

    // ---------------------
    // Required methods
    // ---------------------
    @Override
    public BlockingApi io() {
        return blockingApi;
    }

    @Override
    public void close() throws IOException {
    }

    // Everything else is unused → throw or return null
    @Override
    public ClientId getId() {
        return ClientId.randomId();
    }

    @Override
    public RaftGroupId getGroupId() {
        return null;
    }

    @Override
    public RaftPeerId getLeaderId() {
        return RaftPeerId.valueOf("mock");
    }

    @Override
    public RaftClientRpc getClientRpc() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AdminApi admin() {
        throw new UnsupportedOperationException();
    }

    @Override
    public GroupManagementApi getGroupManagementApi(RaftPeerId server) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SnapshotManagementApi getSnapshotManagementApi() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SnapshotManagementApi getSnapshotManagementApi(RaftPeerId server) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LeaderElectionManagementApi getLeaderElectionManagementApi(RaftPeerId server) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncApi async() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MessageStreamApi getMessageStreamApi() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataStreamApi getDataStreamApi() {
        throw new UnsupportedOperationException();
    }
}
