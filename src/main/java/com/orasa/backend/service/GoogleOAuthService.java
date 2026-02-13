package com.orasa.backend.service;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.orasa.backend.config.OrasaProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final OrasaProperties orasaProperties;

    private static final List<String> SCOPES = List.of(
        "openid",
        "email",
        "profile"
    );

    public String getAuthorizationUrl() {
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            new NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            orasaProperties.getGoogle().getClientId(),
            orasaProperties.getGoogle().getClientSecret(),
            SCOPES
        ).build();

        return flow.newAuthorizationUrl()
            .setRedirectUri(orasaProperties.getGoogle().getRedirectUri())
            .build();
    }

    public GoogleIdToken.Payload exchangeCodeForUserInfo(String code) {
        try {
            GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                orasaProperties.getGoogle().getClientId(),
                orasaProperties.getGoogle().getClientSecret(),
                code,
                orasaProperties.getGoogle().getRedirectUri()
            ).execute();

            GoogleIdToken idToken = tokenResponse.parseIdToken();
            return idToken.getPayload();
        } catch (IOException e) {
            throw new RuntimeException("Failed to exchange Google auth code", e);
        }
    }
}
