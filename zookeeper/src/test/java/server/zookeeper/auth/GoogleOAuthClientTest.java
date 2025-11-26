package server.zookeeper.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthClientTest {

    private GoogleIdTokenVerifier mockVerifier;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        mockVerifier = mock(GoogleIdTokenVerifier.class);
        client = new GoogleOAuthClient(mockVerifier);
    }

    @Test
    void verifyIdToken_validToken_returnsUserInfo() throws Exception {
        String idTokenString = "valid-token";

        GoogleIdToken.Payload payload = mock(GoogleIdToken.Payload.class);
        when(payload.getSubject()).thenReturn("12345");
        when(payload.getEmail()).thenReturn("test@example.com");
        when(payload.get("name")).thenReturn("Test User");
        when(payload.get("picture")).thenReturn("http://example.com/pic.jpg");

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        when(mockToken.getPayload()).thenReturn(payload);

        when(mockVerifier.verify(idTokenString)).thenReturn(mockToken);

        GoogleOAuthClient.GoogleUserInfo info = client.verifyIdToken(idTokenString);

        assertEquals("12345", info.getGoogleId());
        assertEquals("test@example.com", info.getEmail());
        assertEquals("Test User", info.getName());
        assertEquals("http://example.com/pic.jpg", info.getPictureUrl());
    }

    @Test
    void verifyIdToken_invalidToken_throwsException() throws Exception {
        when(mockVerifier.verify("bad")).thenReturn(null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                        () -> client.verifyIdToken("bad"));

        assertEquals("Invalid ID token", ex.getMessage());
    }

    @Test
    void verifyIdToken_verifierThrowsSecurityException_propagates() throws Exception {
        when(mockVerifier.verify("t"))
                .thenThrow(new GeneralSecurityException("Security issue"));

        GeneralSecurityException ex =
                assertThrows(GeneralSecurityException.class,
                        () -> client.verifyIdToken("t"));

        assertEquals("Security issue", ex.getMessage());
    }

    @Test
    void verifyIdToken_verifierThrowsIOException_propagates() throws Exception {
        when(mockVerifier.verify("t"))
                .thenThrow(new IOException("IO issue"));

        IOException ex =
                assertThrows(IOException.class,
                        () -> client.verifyIdToken("t"));

        assertEquals("IO issue", ex.getMessage());
    }
}
