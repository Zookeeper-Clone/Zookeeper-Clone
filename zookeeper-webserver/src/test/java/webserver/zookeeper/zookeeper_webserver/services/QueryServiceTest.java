package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import webserver.zookeeper.zookeeper_webserver.controllers.QueryController;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryServiceTest {

    @Mock
    private ZookeeperClient zookeeperClient;

    private QueryService queryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject mock into service
        queryService = new QueryService(zookeeperClient);
    }

    @Test
    void testReadSuccess() {
        QueryController.ReadRequest request = new QueryController.ReadRequest() {
            @Override
            public String getKey() {
                return "testKey";
            }

            @Override
            public String getDirectory() {
                return null;
            }
        };

        when(zookeeperClient.read("testKey"))
                .thenReturn(ZookeeperClient.QueryResult.success("value"));

        ResponseEntity<String> response = queryService.read(request);

        assertEquals("200 OK", response.getStatusCode().toString());
        verify(zookeeperClient).read("testKey");
    }

    @Test
    void testReadFailure() {
        QueryController.ReadRequest request = new QueryController.ReadRequest() {
            @Override
            public String getKey() {
                return "badKey";
            }

            @Override
            public String getDirectory() {
                return null;
            }
        };

        when(zookeeperClient.read("badKey"))
                .thenReturn(ZookeeperClient.QueryResult.failure("Not found"));

        ResponseEntity<String> response = queryService.read(request);

        assertEquals("400 BAD_REQUEST", response.getStatusCode().toString());
        assertEquals("Not found", response.getBody());
        verify(zookeeperClient).read("badKey");
    }

    @Test
    void testWriteCreate() {
        QueryController.WriteRequest request = new QueryController.WriteRequest() {
            @Override
            public String getKey() {
                return "key";
            }

            @Override
            public String getValue() {
                return "val";
            }

            @Override
            public String getDirectory() {
                return null;
            }

            @Override
            public boolean getIsUpdate() {
                return false; // Create operation
            }
        };

        when(zookeeperClient.create("key", "val", false))
                .thenReturn(ZookeeperClient.QueryResult.success("ok"));

        ResponseEntity<String> response = queryService.write(request);

        assertEquals("200 OK", response.getStatusCode().toString());
        verify(zookeeperClient).create("key", "val", false);
    }

    @Test
    void testWriteUpdate() {
        QueryController.WriteRequest request = new QueryController.WriteRequest() {
            @Override
            public String getKey() {
                return "existingKey";
            }

            @Override
            public String getValue() {
                return "newVal";
            }

            @Override
            public String getDirectory() {
                return null;
            }

            @Override
            public boolean getIsUpdate() {
                return true;
            }
        };

        when(zookeeperClient.update("existingKey", "newVal"))
                .thenReturn(ZookeeperClient.QueryResult.success("updated"));

        ResponseEntity<String> response = queryService.write(request);

        assertEquals("200 OK", response.getStatusCode().toString());
        verify(zookeeperClient).update("existingKey", "newVal");
    }

    @Test
    void testWriteCreateFailure() {
        QueryController.WriteRequest request = new QueryController.WriteRequest() {
            @Override
            public String getKey() {
                return "key";
            }

            @Override
            public String getValue() {
                return "val";
            }

            @Override
            public String getDirectory() {
                return null;
            }

            @Override
            public boolean getIsUpdate() {
                return false;
            }
        };

        when(zookeeperClient.create("key", "val", false))
                .thenReturn(ZookeeperClient.QueryResult.failure("Key already exists"));

        ResponseEntity<String> response = queryService.write(request);

        assertEquals("400 BAD_REQUEST", response.getStatusCode().toString());
        assertEquals("Key already exists", response.getBody());
        verify(zookeeperClient).create("key", "val", false);
    }

    @Test
    void testWriteUpdateFailure() {
        QueryController.WriteRequest request = new QueryController.WriteRequest() {
            @Override
            public String getKey() {
                return "nonExistentKey";
            }

            @Override
            public String getValue() {
                return "newVal";
            }

            @Override
            public String getDirectory() {
                return null;
            }

            @Override
            public boolean getIsUpdate() {
                return true;
            }
        };

        when(zookeeperClient.update("nonExistentKey", "newVal"))
                .thenReturn(ZookeeperClient.QueryResult.failure("Key not found"));

        ResponseEntity<String> response = queryService.write(request);

        assertEquals("400 BAD_REQUEST", response.getStatusCode().toString());
        assertEquals("Key not found", response.getBody());
        verify(zookeeperClient).update("nonExistentKey", "newVal");
    }

    @Test
    void testDeleteFailure() {
        QueryController.DeleteRequest request = new QueryController.DeleteRequest() {
            @Override
            public String getKey() {
                return "key";
            }

            @Override
            public String getDirectory() {
                return null;
            }
        };

        when(zookeeperClient.delete("key"))
                .thenReturn(ZookeeperClient.QueryResult.failure("cannot delete"));

        ResponseEntity<String> response = queryService.delete(request);

        assertEquals("400 BAD_REQUEST", response.getStatusCode().toString());
        assertEquals("cannot delete", response.getBody());
        verify(zookeeperClient).delete("key");
    }
}
