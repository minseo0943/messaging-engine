package com.jdc.gateway.controller;

import com.jdc.gateway.routing.GatewayRouter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GatewayProxyControllerTest {

    @Mock
    private GatewayRouter gatewayRouter;

    @InjectMocks
    private GatewayProxyController gatewayProxyController;

    @Test
    @DisplayName("chat 프록시 요청 시 GatewayRouter로 라우팅되는 테스트")
    void proxyChatService_shouldDelegateToRouter() throws IOException {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        gatewayProxyController.proxyChatService(request, response);

        // Then
        then(gatewayRouter).should().route(request, response);
    }

    @Test
    @DisplayName("query 프록시 요청 시 GatewayRouter로 라우팅되는 테스트")
    void proxyQueryService_shouldDelegateToRouter() throws IOException {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        gatewayProxyController.proxyQueryService(request, response);

        // Then
        then(gatewayRouter).should().route(request, response);
    }

    @Test
    @DisplayName("presence 프록시 요청 시 GatewayRouter로 라우팅되는 테스트")
    void proxyPresenceService_shouldDelegateToRouter() throws IOException {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        gatewayProxyController.proxyPresenceService(request, response);

        // Then
        then(gatewayRouter).should().route(request, response);
    }

    @Test
    @DisplayName("ai 프록시 요청 시 GatewayRouter로 라우팅되는 테스트")
    void proxyAiService_shouldDelegateToRouter() throws IOException {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        gatewayProxyController.proxyAiService(request, response);

        // Then
        then(gatewayRouter).should().route(request, response);
    }
}
