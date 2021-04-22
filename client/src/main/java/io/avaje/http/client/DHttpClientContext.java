package io.avaje.http.client;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class DHttpClientContext implements HttpClientContext {

  private final HttpClient httpClient;
  private final String baseUrl;
  private final Duration requestTimeout;
  private final BodyAdapter bodyAdapter;
  private final RequestListener requestListener;
  private final RequestIntercept requestIntercept;
  private final boolean withAuthToken;
  private final AuthTokenProvider authTokenProvider;
  private final AtomicReference<AuthToken> tokenRef = new AtomicReference<>();

  DHttpClientContext(HttpClient httpClient, String baseUrl, Duration requestTimeout, BodyAdapter bodyAdapter, RequestListener requestListener, AuthTokenProvider authTokenProvider, RequestIntercept intercept) {
    this.httpClient = httpClient;
    this.baseUrl = baseUrl;
    this.requestTimeout = requestTimeout;
    this.bodyAdapter = bodyAdapter;
    this.requestListener = requestListener;
    this.authTokenProvider = authTokenProvider;
    this.withAuthToken = authTokenProvider != null;
    this.requestIntercept = intercept;
  }

  @Override
  public HttpClientRequest request() {
    return new DHttpClientRequest(this, requestTimeout);
  }

  @Override
  public BodyAdapter converters() {
    return bodyAdapter;
  }

  @Override
  public UrlBuilder url() {
    return new UrlBuilder(baseUrl);
  }

  @Override
  public HttpClient httpClient() {
    return httpClient;
  }

  @Override
  public void checkResponse(HttpResponse<?> response) {
    if (response.statusCode() >= 300) {
      throw new HttpException(response, this);
    }
  }

  void check(HttpResponse<byte[]> response) {
    if (response.statusCode() >= 300) {
      throw new HttpException(this, response);
    }
  }

  @Override
  public BodyContent readContent(HttpResponse<byte[]> httpResponse) {
    byte[] bodyBytes = decodeContent(httpResponse);
    final String contentType = getContentType(httpResponse);
    return new BodyContent(contentType, bodyBytes);
  }

  String getContentType(HttpResponse<byte[]> httpResponse) {
    return firstHeader(httpResponse.headers(), "Content-Type", "content-type");
  }

  String getContentEncoding(HttpResponse<byte[]> httpResponse) {
    return firstHeader(httpResponse.headers(), "Content-Encoding", "content-encoding");
  }

  @Override
  public byte[] decodeContent(String encoding, byte[] body) {
    if (encoding.equals("gzip")) {
      return GzipUtil.gzipDecode(body);
    }
    // todo: register decoders with context and use them
    return body;
  }

  public byte[] decodeContent(HttpResponse<byte[]> httpResponse) {
    String encoding = getContentEncoding(httpResponse);
    return encoding == null ? httpResponse.body() : decodeContent(encoding, httpResponse.body());
  }

  String firstHeader(HttpHeaders headers, String... names) {
    final Map<String, List<String>> map = headers.map();
    for (String key : names) {
      final List<String> values = map.get(key);
      if (values != null && !values.isEmpty()) {
        return values.get(0);
      }
    }
    return null;
  }

  <T> HttpResponse<T> send(HttpRequest.Builder requestBuilder, HttpResponse.BodyHandler<T> bodyHandler) {
    final HttpRequest request = applyFilters(requestBuilder).build();
    try {
      return httpClient.send(request, bodyHandler);
    } catch (IOException e) {
      throw new HttpException(499, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HttpException(499, e);
    }
  }

  private HttpRequest.Builder applyFilters(HttpRequest.Builder hreq) {
    return hreq;
  }

  BodyContent write(Object bean, String contentType) {
    return bodyAdapter.beanWriter(bean.getClass()).write(bean, contentType);
  }

  <T> T readBean(Class<T> cls, BodyContent content) {
    return bodyAdapter.beanReader(cls).read(content);
  }

  <T> List<T> readList(Class<T> cls, BodyContent content) {
    return bodyAdapter.listReader(cls).read(content);
  }

  void afterResponse(DHttpClientRequest request) {
    if (requestListener != null) {
      requestListener.response(request.listenerEvent());
    }
    if (requestIntercept != null) {
      requestIntercept.afterResponse(request.response(), request);
    }
  }

  void beforeRequest(DHttpClientRequest request) {
    if (withAuthToken && !request.isSkipAuthToken()) {
      request.header("Authorization", "Bearer " + authToken());
    }
    if (requestIntercept != null) {
      requestIntercept.beforeRequest(request);
    }
  }

  private String authToken() {
    AuthToken authToken = tokenRef.get();
    if (authToken == null || authToken.isExpired()) {
      authToken = authTokenProvider.obtainToken(request().skipAuthToken());
      tokenRef.set(authToken);
    }
    return authToken.token();
  }

}
