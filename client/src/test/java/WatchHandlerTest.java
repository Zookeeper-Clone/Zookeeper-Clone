import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import client.zookeeper.ZookeeperClient;
import client.zookeeper.watches.Watcher;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.Test;

import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;

import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.WatchEvent;
import server.zookeeper.proto.query.WatchEventType;

public class WatchHandlerTest {

    @Test
    public void testWatchRequestProcessesEventAsync() throws Exception {
        // Prepare a QueryResponse containing a WatchEvent (PUT_EVENT)
        WatchEvent watchEvent = WatchEvent.newBuilder()
                .setEventType(WatchEventType.PUT_EVENT)
                .setKey("myKey")
                .setColumnFamily("myCF")
                .build();

        QueryResponse response = QueryResponse.newBuilder()
                .setSuccess(true)
                .setErrorMessage("")
                .setWatchEvents(watchEvent)
                .build();

        // Mock the Raft Protocol Message to return raw QueryResponse bytes as content
        Message protoMessage = mock(Message.class);
        when(protoMessage.getContent()).thenReturn(ByteString.copyFrom(response.toByteArray()));

        // Mock RaftClient and RaftClientReply (deep stubs so async().sendReadOnly(...) works)
        RaftClient raftClient = mock(RaftClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        RaftClientReply reply = mock(RaftClientReply.class);

        when(reply.isSuccess()).thenReturn(true);
        when(reply.getMessage()).thenReturn(protoMessage);

        // Make async().sendReadOnly(...) return a completed future with our reply
        when(raftClient.async().sendReadOnly(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(reply));

        // Use a latch to wait for asynchronous watcher processing
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WatchEvent> received = new AtomicReference<>();

        Watcher watcher = event -> {
            received.set(event);
            latch.countDown();
        };

        ZookeeperClient client = new ZookeeperClient(raftClient, watcher);

        // Trigger watch
        client.addWatch("myKey", "myDir");

        // Wait for up to 2 seconds for the async processing to occur
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Watcher.process should be invoked asynchronously within timeout");

        WatchEvent ev = received.get();
        assertNotNull(ev, "WatchEvent should be delivered to watcher");
        assertEquals(WatchEventType.PUT_EVENT, ev.getEventType());
        assertEquals("myKey", ev.getKey());
        assertEquals("myCF", ev.getColumnFamily());
    }
}
