package com.jdc.gateway.controller;

import com.jdc.gateway.routing.GatewayRouter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class GatewayProxyController {

    private final GatewayRouter gatewayRouter;

    @RequestMapping("/api/chat/**")
    public void proxyChatService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        gatewayRouter.route(request, response);
    }

    @RequestMapping("/api/query/**")
    public void proxyQueryService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        gatewayRouter.route(request, response);
    }

    @RequestMapping("/api/presence/**")
    public void proxyPresenceService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        gatewayRouter.route(request, response);
    }

    @RequestMapping("/api/ai/**")
    public void proxyAiService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        gatewayRouter.route(request, response);
    }
}
