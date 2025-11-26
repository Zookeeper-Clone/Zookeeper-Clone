package server.zookeeper.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Objects;

/**
 * Wrapper for Google's OAuth 2.0 ID token verification library.
 *
 * Responsibilities:
 * - Verifies Google ID tokens using Google's official verification library
 * - Validates cryptographic signature (ensures token is from Google)
 * - Validates token expiration (ensures token is still valid)
 * - Validates audience claim (ensures token is for this application)
 * - Extracts user information from verified token payload
 *
 * verifyIdToken(idTokenString) returns GoogleUserInfo containing:
 * - googleId: Google's unique user identifier (subject claim)
 * - email: User's email address
 * - name: User's full name
 * - pictureUrl: URL to user's profile picture
 *
 * Throws:
 * - GeneralSecurityException: If token signature is invalid
 * - IOException: If verification request fails
 * - IllegalArgumentException: If token is invalid or expired
 *
 * Supports constructor injection for testing with mock verifiers.
 */
public class GoogleOAuthClient {
    private final GoogleIdTokenVerifier verifier;

    // Production constructor (unchanged)
    public GoogleOAuthClient(String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    // Testable constructor — allows injecting a mock verifier
    public GoogleOAuthClient(GoogleIdTokenVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public GoogleUserInfo verifyIdToken(String idTokenString)
            throws GeneralSecurityException, IOException {

        GoogleIdToken idToken = verifier.verify(idTokenString);

        if (idToken == null) {
            throw new IllegalArgumentException("Invalid ID token");
        }

        // Extract the payload (user data)
        GoogleIdToken.Payload payload = idToken.getPayload();

        return new GoogleUserInfo(
                payload.getSubject(),   // // Google's unique user ID
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture")
        );
    }

    public static class GoogleUserInfo {
        private final String googleId;
        private final String email;
        private final String name;
        private final String pictureUrl;

        public GoogleUserInfo(String googleId, String email, String name, String pictureUrl) {
            this.googleId = googleId;
            this.email = email;
            this.name = name;
            this.pictureUrl = pictureUrl;
        }

        public String getGoogleId() { return googleId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getPictureUrl() { return pictureUrl; }
    }
}
