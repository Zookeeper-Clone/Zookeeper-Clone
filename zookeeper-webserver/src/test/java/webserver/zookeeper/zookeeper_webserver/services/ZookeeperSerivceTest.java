package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

class ZookeeperServiceTest {

    @Mock
    private ZookeeperClient zookeeperClient;

    private ZookeeperService zookeeperService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        zookeeperService = new ZookeeperService(zookeeperClient);
    }

    @Test
    void testSetTokenDelegatesToClient() {
        String token = "abc123";
        zookeeperService.setToken(token);

        verify(zookeeperClient).setSessionToken(token);
    }
}

