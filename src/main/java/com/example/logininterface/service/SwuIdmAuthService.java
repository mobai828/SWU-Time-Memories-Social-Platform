package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class SwuIdmAuthService {

    private static final String USER_AGENT = "Mozilla/5.0";

    private final AppProperties appProperties;
    private final OkHttpClient httpClient;

    public SwuIdmAuthService(AppProperties appProperties) {
        this.appProperties = appProperties;
        AppProperties.SwuIdm swuIdm = appProperties.getSwuIdm();
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .cookieJar(okhttp3.CookieJar.NO_COOKIES)
                .connectTimeout(Duration.ofSeconds(swuIdm.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(swuIdm.getReadTimeoutSeconds()))
                .build();
    }

    public String buildLoginRedirectUrl(HttpServletRequest request) {
        String serviceUrl = buildServiceUrl(request);
        String loginUrl = appendQueryParameter(appProperties.getSwuIdm().getLoginUrl(), "service", serviceUrl);
        if (appProperties.getSwuIdm().isForceRenew()) {
            return appendQueryParameter(loginUrl, "renew", "true");
        }
        return loginUrl;
    }

    public String buildLogoutRedirectUrl(HttpServletRequest request) {
        String logoutReturnUrl = buildLogoutReturnUrl(request);
        return appendQueryParameter(appProperties.getSwuIdm().getLogoutUrl(), "service", logoutReturnUrl);
    }

    public AuthenticatedCampusUser authenticate(String ticket, HttpServletRequest request) {
        if (ticket == null || ticket.isBlank()) {
            throw new ValidationException("统一认证未返回登录票据");
        }
        String serviceUrl = buildServiceUrl(request);
        Request httpRequest = new Request.Builder()
                .url(buildServiceValidateUrl(ticket, serviceUrl))
                .get()
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ValidationException("统一认证票据校验失败，状态码：" + response.code());
            }
            String xml = response.body().string();
            return parseAuthenticatedUser(xml)
                    .orElseThrow(() -> new ValidationException(resolveFailureMessage(xml)));
        } catch (IOException exception) {
            throw new ValidationException("统一认证票据校验失败：" + exception.getMessage());
        }
    }

    public String buildServiceUrl(HttpServletRequest request) {
        String callbackPath = normalizeCallbackPath(appProperties.getSwuIdm().getCallbackPath());
        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(request.getContextPath() + callbackPath)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    public String buildLogoutReturnUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequest(request)
                .replacePath(request.getContextPath() + "/login")
                .replaceQuery("logout")
                .build()
                .toUriString();
    }

    @PreDestroy
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private String buildServiceValidateUrl(String ticket, String serviceUrl) {
        return appendQueryParameter(
                appendQueryParameter(appProperties.getSwuIdm().getServiceValidateUrl(), "service", serviceUrl),
                "ticket",
                ticket
        );
    }

    private Optional<AuthenticatedCampusUser> parseAuthenticatedUser(String xml) {
        try {
            Document document = parseXml(xml);
            Element root = document.getDocumentElement();
            Element successElement = firstDescendantByLocalName(root, "authenticationSuccess");
            if (successElement == null) {
                return Optional.empty();
            }
            String userId = textOfChild(successElement, "user")
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> new ValidationException("统一认证返回成功，但缺少用户标识"));
            Element attributes = firstDescendantByLocalName(successElement, "attributes");
            String displayName = textOfChild(attributes, "displayName")
                    .or(() -> textOfChild(attributes, "cn"))
                    .or(() -> textOfChild(attributes, "realName"))
                    .orElse(userId);
            String email = textOfChild(attributes, "mail")
                    .or(() -> textOfChild(attributes, "email"))
                    .orElse(userId + "@swu-sso.local");
            return Optional.of(new AuthenticatedCampusUser(userId.trim(), displayName.trim(), email.trim()));
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("统一认证返回内容解析失败");
        }
    }

    private String resolveFailureMessage(String xml) {
        try {
            Document document = parseXml(xml);
            Element failureElement = firstDescendantByLocalName(document.getDocumentElement(), "authenticationFailure");
            if (failureElement != null && failureElement.getTextContent() != null && !failureElement.getTextContent().isBlank()) {
                return "统一认证登录失败：" + failureElement.getTextContent().trim();
            }
        } catch (Exception ignored) {
            // ignore and fall back to generic message
        }
        return "统一认证登录失败，未通过票据校验";
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private Element firstDescendantByLocalName(Element root, String localName) {
        if (root == null) {
            return null;
        }
        if (matchesLocalName(root, localName)) {
            return root;
        }
        for (int index = 0; index < root.getChildNodes().getLength(); index++) {
            Node child = root.getChildNodes().item(index);
            if (child instanceof Element element) {
                Element candidate = firstDescendantByLocalName(element, localName);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Optional<String> textOfChild(Element parent, String localName) {
        Element child = firstDescendantByLocalName(parent, localName);
        if (child == null || child.getTextContent() == null) {
            return Optional.empty();
        }
        String text = child.getTextContent().trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private boolean matchesLocalName(Element element, String expectedLocalName) {
        String localName = element.getLocalName();
        if (expectedLocalName.equals(localName)) {
            return true;
        }
        return expectedLocalName.equals(element.getNodeName())
                || element.getNodeName().endsWith(":" + expectedLocalName);
    }

    private String normalizeCallbackPath(String callbackPath) {
        String value = callbackPath == null || callbackPath.isBlank() ? "/login/swu/callback" : callbackPath.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String appendQueryParameter(String url, String key, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + urlEncode(value);
    }

    public record AuthenticatedCampusUser(String userId, String displayName, String email) {
    }
}
