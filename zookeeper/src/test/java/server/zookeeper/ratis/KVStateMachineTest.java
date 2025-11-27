package server.zookeeper.ratis;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.DB.DataBase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KVStateMachineTest {

    @Mock
    private DataBase mockDb;

    @Mock
    private TransactionContext mockTrx;

    @Mock
    private RaftProtos.LogEntryProto mockLogEntry;

    @Mock
    private RaftProtos.StateMachineLogEntryProto mockSMLogEntry;

    private MockedStatic<GoogleNetHttpTransport> transportMock;
    private KVStateMachine stateMachine;

    @BeforeEach
    void setUp() throws Exception {
        transportMock = mockStatic(GoogleNetHttpTransport.class);
        transportMock.when(GoogleNetHttpTransport::newTrustedTransport)
                .thenReturn(new NetHttpTransport());

        stateMachine = new KVStateMachine(mockDb);
    }

    @AfterEach
    void tearDown() {
        if (transportMock != null) {
            transportMock.close();
        }
    }

    @Test
    @DisplayName("Constructor should initialize successfully with valid dependencies")
    void testInitialization() {
        assertNotNull(stateMachine, "StateMachine should be initialized");
    }

    @Test
    @DisplayName("Constructor should throw RuntimeException if Google Transport fails")
    void testInitializationFailure() {
        transportMock.close();

        try (MockedStatic<GoogleNetHttpTransport> badMock = mockStatic(GoogleNetHttpTransport.class)) {
            badMock.when(GoogleNetHttpTransport::newTrustedTransport)
                    .thenThrow(new java.security.GeneralSecurityException("SSL Error"));

            assertThrows(RuntimeException.class, () -> new KVStateMachine(mockDb),
                    "Should wrap initialization exceptions in RuntimeException");
        }
        transportMock = mockStatic(GoogleNetHttpTransport.class);
    }

    @Test
    @DisplayName("ApplyTransaction should unwrap LogEntry and return Future")
    void testApplyTransaction() throws ExecutionException, InterruptedException {
        String testPayload = "test_payload";
        ByteString byteStringPayload = ByteString.copyFromUtf8(testPayload);

        when(mockTrx.getLogEntry()).thenReturn(mockLogEntry);
        when(mockLogEntry.getStateMachineLogEntry()).thenReturn(mockSMLogEntry);
        when(mockSMLogEntry.getLogData()).thenReturn(byteStringPayload);

        CompletableFuture<Message> future = stateMachine.applyTransaction(mockTrx);

        assertNotNull(future);
        Message response = future.get();
        assertNotNull(response);
        verify(mockTrx, atLeastOnce()).getLogEntry();
    }

    @Test
    @DisplayName("ApplyTransaction should handle exceptions gracefully")
    void testApplyTransactionWithNullData() throws ExecutionException, InterruptedException {
        when(mockTrx.getLogEntry()).thenReturn(null);

        CompletableFuture<Message> future = stateMachine.applyTransaction(mockTrx);

        assertNotNull(future);
        Message response = future.get();
        String content = response.getContent().toStringUtf8();
        assertTrue(content.startsWith("ERROR:"), "Should return an error message when transaction fails");
    }

    @Test
    @DisplayName("Query should process message and return Future")
    void testQuery() throws ExecutionException, InterruptedException {
        String queryStr = "some_query";
        Message request = Message.valueOf(queryStr);

        CompletableFuture<Message> future = stateMachine.query(request);

        assertNotNull(future);
        Message response = future.get();
        assertNotNull(response);
    }

    @Test
    @DisplayName("Query should handle exceptions gracefully")
    void testQueryWithException() throws ExecutionException, InterruptedException {

        Message badMessage = mock(Message.class);
        when(badMessage.getContent()).thenThrow(new RuntimeException("Content Error"));

        CompletableFuture<Message> future = stateMachine.query(badMessage);

        assertNotNull(future);
        Message response = future.get();
        assertTrue(response.getContent().toStringUtf8().contains("ERROR"), "Should catch exceptions in query");
    }
}