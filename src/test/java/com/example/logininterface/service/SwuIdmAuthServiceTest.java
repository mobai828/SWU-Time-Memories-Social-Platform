package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import jakarta.validation.ValidationException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwuIdmAuthServiceTest {

    private final MockWebServer mockWebServer = new MockWebServer();

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void shouldBuildLoginRedirectUrl() {
        SwuIdmAuthService service = new SwuIdmAuthService(createProperties());
        MockHttpServletRequest request = createRequest("/auth/swu");

        String redirectUrl = service.buildLoginRedirectUrl(request);

        assertTrue(redirectUrl.startsWith("https://uaaap.swu.edu.cn/cas/login?service="));
        assertTrue(redirectUrl.contains("http%3A%2F%2Flocalhost%3A8081%2Flogin%2Fswu%2Fcallback"));
        assertTrue(redirectUrl.contains("renew=true"));
    }

    @Test
    void shouldBuildLogoutRedirectUrl() {
        SwuIdmAuthService service = new SwuIdmAuthService(createProperties());
        MockHttpServletRequest request = createRequest("/logout");

        String redirectUrl = service.buildLogoutRedirectUrl(request);

        assertTrue(redirectUrl.startsWith("https://uaaap.swu.edu.cn/cas/logout?service="));
        assertTrue(redirectUrl.contains("http%3A%2F%2Flocalhost%3A8081%2Flogin%3Flogout"));
    }

    @Test
    void shouldValidateTicketAndExtractUser() throws Exception {
        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                          <cas:authenticationSuccess>
                            <cas:user>20240001</cas:user>
                            <cas:attributes>
                              <cas:displayName>张三</cas:displayName>
                              <cas:mail>zhangsan@swu.edu.cn</cas:mail>
                            </cas:attributes>
                          </cas:authenticationSuccess>
                        </cas:serviceResponse>
                        """));

        SwuIdmAuthService service = new SwuIdmAuthService(createProperties());
        MockHttpServletRequest request = createRequest("/login/swu/callback");

        var user = service.authenticate("ST-12345", request);

        assertEquals("20240001", user.userId());
        assertEquals("张三", user.displayName());
        assertEquals("zhangsan@swu.edu.cn", user.email());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertTrue(recordedRequest.getPath().contains("/cas/serviceValidate"));
        assertTrue(recordedRequest.getPath().contains("ticket=ST-12345"));
        assertTrue(recordedRequest.getPath().contains("service=http%3A%2F%2Flocalhost%3A8081%2Flogin%2Fswu%2Fcallback"));
    }

    @Test
    void shouldThrowWhenTicketValidationFails() throws Exception {
        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        <cas:serviceResponse xmlns:cas="http://www.yale.edu/tp/cas">
                          <cas:authenticationFailure code="INVALID_TICKET">ticket 无效</cas:authenticationFailure>
                        </cas:serviceResponse>
                        """));

        SwuIdmAuthService service = new SwuIdmAuthService(createProperties());
        MockHttpServletRequest request = createRequest("/login/swu/callback");

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.authenticate("ST-invalid", request)
        );
        assertTrue(exception.getMessage().contains("ticket 无效"));
    }

    private AppProperties createProperties() {
        AppProperties properties = new AppProperties();
        AppProperties.SwuIdm swuIdm = new AppProperties.SwuIdm();
        swuIdm.setLoginUrl("https://uaaap.swu.edu.cn/cas/login");
        swuIdm.setLogoutUrl("https://uaaap.swu.edu.cn/cas/logout");
        swuIdm.setServiceValidateUrl(mockWebServer.url("/cas/serviceValidate").toString());
        swuIdm.setCallbackPath("/login/swu/callback");
        swuIdm.setForceRenew(true);
        swuIdm.setConnectTimeoutSeconds(5);
        swuIdm.setReadTimeoutSeconds(5);
        properties.setSwuIdm(swuIdm);
        return properties;
    }

    private MockHttpServletRequest createRequest(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8081);
        request.setContextPath("");
        return request;
    }
}
