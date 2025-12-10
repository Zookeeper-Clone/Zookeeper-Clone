package webserver.zookeeper.zookeeper_webserver.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import webserver.zookeeper.zookeeper_webserver.services.QueryService;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

public class QueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QueryService queryService;

    @Mock
    private ZookeeperService zookeeperService;

    @InjectMocks
    private QueryController queryController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(queryController).build();
    }

    @Test
    void testRead() throws Exception {
        QueryController.ReadRequest request = new QueryController.ReadRequest();
        ResponseEntity<String> responseEntity = ResponseEntity.ok("read-success");
        when(queryService.read(any())).thenReturn(responseEntity);

        mockMvc.perform(post("/query/read")
                        .cookie(new Cookie("SESSION_TOKEN", "token123"))
                        .contentType("application/json")
                        .content("{\"key\":\"myKey\",\"directory\":\"myDir\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("read-success"));

        verify(zookeeperService).setToken("token123");
        verify(queryService).read(any(QueryController.ReadRequest.class));
    }

    @Test
    void testWrite() throws Exception {
        QueryController.WriteRequest request = new QueryController.WriteRequest();
        ResponseEntity<String> responseEntity = ResponseEntity.ok("write-success");
        when(queryService.write(any())).thenReturn(responseEntity);

        mockMvc.perform(post("/query/write")
                        .cookie(new Cookie("SESSION_TOKEN", "token456"))
                        .contentType("application/json")
                        .content("{\"key\":\"myKey\",\"directory\":\"myDir\",\"value\":\"myValue\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("write-success"));

        verify(zookeeperService).setToken("token456");
        verify(queryService).write(any(QueryController.WriteRequest.class));
    }

    @Test
    void testDelete() throws Exception {
        QueryController.DeleteRequest request = new QueryController.DeleteRequest();
        ResponseEntity<String> responseEntity = ResponseEntity.ok("delete-success");
        when(queryService.delete(any())).thenReturn(responseEntity);

        mockMvc.perform(post("/query/delete")
                        .cookie(new Cookie("SESSION_TOKEN", "token789"))
                        .contentType("application/json")
                        .content("{\"key\":\"myKey\",\"directory\":\"myDir\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("delete-success"));

        verify(zookeeperService).setToken("token789");
        verify(queryService).delete(any(QueryController.DeleteRequest.class));
    }
}
