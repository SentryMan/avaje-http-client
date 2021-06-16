package io.avaje.http.client;

import org.example.webserver.ErrorResponse;
import org.example.webserver.HelloDto;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class HelloControllerTest extends BaseWebTest {

  final HttpClientContext clientContext = client();

  @Test
  void get_stream() {

    final Stream<SimpleData> stream = clientContext.request()
      .path("hello").path("stream")
      .GET()
      .stream(SimpleData.class);

    final List<SimpleData> data = stream.collect(Collectors.toList());

    assertThat(data).hasSize(4);
    final SimpleData first = data.get(0);
    assertThat(first.id).isEqualTo(1);
    assertThat(first.name).isEqualTo("one");
  }

  @Test
  void get_helloMessage() {

    final HttpResponse<String> hres = clientContext.request()
      .path("hello").path("message")
      .GET().asString();

    assertThat(hres.body()).contains("hello world");
    assertThat(hres.statusCode()).isEqualTo(200);
  }

  @Test
  void get_helloMessage_via_url() {

    final HttpResponse<String> hres = clientContext.request()
      .url("http://127.0.0.1:8887")
      .path("hello").path("message")
      .GET().asString();

    assertThat(hres.body()).contains("hello world");
    assertThat(hres.statusCode()).isEqualTo(200);
  }

  @Test
  void get_hello_returningListOfBeans() {

    final List<HelloDto> helloDtos = clientContext.request()
      .path("hello")
      .GET().list(HelloDto.class);

    assertThat(helloDtos).hasSize(2);
  }

  @Test
  void get_withPathParamAndQueryParam_returningBean() {

    final HelloDto dto = clientContext.request()
      .path("hello/43/2020-03-05").queryParam("otherParam", "other").queryParam("foo", null)
      .GET()
      .bean(HelloDto.class);

    assertThat(dto.id).isEqualTo(43L);
    assertThat(dto.name).isEqualTo("2020-03-05");
    assertThat(dto.otherParam).isEqualTo("other");
  }

  @Test
  void post_bean_returningBean_usingExplicitConverters() {

    HelloDto dto = new HelloDto(12, "rob", "other");

    final BodyWriter from = clientContext.converters().beanWriter(HelloDto.class);
    final BodyReader<HelloDto> toDto = clientContext.converters().beanReader(HelloDto.class);

    final HelloDto bean = clientContext.request()
      .path("hello")
      .body(from.write(dto))
      .POST()
      .read(toDto);

    assertEquals("posted", bean.name);
    assertEquals(12, bean.id);
  }

  @Test
  void post_bean_returningVoid() {

    HelloDto dto = new HelloDto(12, "rob", "other");

    final HttpResponse<Void> res = clientContext.request()
      .path("hello/savebean/foo")
      .body(dto)
      .POST()
      .asDiscarding();

    assertThat(res.statusCode()).isEqualTo(201);
  }

  @Test
  void postForm() {

    final HttpResponse<Void> res = clientContext.request()
      .path("hello/saveform")
      .formParam("name", "Bazz")
      .formParam("email", "user@foo.com")
      .formParam("url", "http://foo.com")
      .formParam("startDate", "2030-12-03")
      .POST()
      .asDiscarding();

    assertThat(res.statusCode()).isEqualTo(201);
  }

  @Test
  void postForm_returningBean() {

    final HttpResponse<Void> res = clientContext.request()
      .path("hello/saveform")
      .formParam("name", "Bazz")
      .formParam("email", "user@foo.com")
      .formParam("url", "http://foo.com")
      .formParam("startDate", "2030-12-03")
      .POST()
      .asDiscarding();

    assertThat(res.statusCode()).isEqualTo(201);

    final HelloDto bean = clientContext.request()
      .path("hello/saveform3")
      .formParam("name", "Bax")
      .formParam("email", "Bax@foo.com")
      .formParam("url", "http://foo.com")
      .formParam("startDate", "2030-12-03")
      .POST()
      .bean(HelloDto.class);

    assertThat(bean.name).isEqualTo("Bax");
    assertThat(bean.otherParam).isEqualTo("Bax@foo.com");
    assertThat(bean.id).isEqualTo(52);
  }

  @Test
  void postForm_asVoid_validResponse() {
    HttpResponse<Void> res = clientContext.request()
      .path("hello/saveform")
      .formParam("name", "baz")
      .formParam("email", "user@foo.com")
      .formParam("url", "http://foo")
      .POST()
      .asVoid();

    assertEquals(201, res.statusCode());
  }

  @Test
  void postForm_asVoid_invokesValidation_expect_badRequest_extractError() {
    try {
      clientContext.request()
        .path("hello/saveform")
        .formParam("email", "user@foo.com")
        .formParam("url", "notAValidUrl")
        .POST()
        .asVoid();

      fail();

    } catch (HttpException e) {
      assertEquals(422, e.getStatusCode());

      final HttpResponse<?> httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(422, httpResponse.statusCode());

      final ErrorResponse errorResponse = e.bean(ErrorResponse.class);

      final Map<String, String> errorMap = errorResponse.getErrors();
      assertThat(errorMap.get("url")).isEqualTo("must be a valid URL");
      assertThat(errorMap.get("name")).isEqualTo("must not be null");
    }
  }

  @Test
  void postForm_asBytes_validation_expect_badRequest_extractError() {
    try {
      clientContext.request()
        .path("hello/saveform")
        .formParam("email", "user@foo.com")
        .formParam("url", "notAValidUrl")
        .POST().asVoid();

      fail();

    } catch (HttpException e) {
      assertEquals(422, e.getStatusCode());

      final HttpResponse<?> httpResponse = e.getHttpResponse();
      assertNotNull(httpResponse);
      assertEquals(422, httpResponse.statusCode());

      final ErrorResponse errorResponse = e.bean(ErrorResponse.class);
      final Map<String, String> errorMap = errorResponse.getErrors();
      assertThat(errorMap.get("url")).isEqualTo("must be a valid URL");
      assertThat(errorMap.get("name")).isEqualTo("must not be null");

      String rawBody = e.bodyAsString();
      assertThat(rawBody).contains("must be a valid URL");

      final byte[] rawBytes = e.bodyAsBytes();
      assertThat(rawBytes).isNotNull();
    }
  }

  @Test
  void delete() {
    final HttpResponse<Void> res =
      clientContext.request()
        .path("hello/52")
        .DELETE()
        .asDiscarding();

    assertThat(res.statusCode()).isEqualTo(204);
  }

  @Test
  void get_withMatrixParam() {

    final HttpResponse<String> httpRes = clientContext.request()
      .path("hello/withMatrix/2011")
      .matrixParam("author", "rob")
      .matrixParam("country", "nz")
      .path("foo")
      .queryParam("extra", "banana")
      .GET()
      .asString();

    assertEquals(200, httpRes.statusCode());
    assertEquals("yr:2011 au:rob co:nz other:foo extra:banana", httpRes.body());
  }

  public static class SimpleData {
    public long id;
    public String name;
  }
}
