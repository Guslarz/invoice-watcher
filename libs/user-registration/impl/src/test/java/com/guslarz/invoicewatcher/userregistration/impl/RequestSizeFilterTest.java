package com.guslarz.invoicewatcher.userregistration.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeFilterTest {
  @Test
  @DisplayName("REG-004: oversized request bodies are rejected before parsing or persistence")
  void rejectsOversizedBody() throws Exception {
    // given
    var uut = new RequestSizeFilter();
    var request = new MockHttpServletRequest("POST", "/api/v1/registration");
    request.setContent(new byte[32769]);
    var response = new MockHttpServletResponse();
    var invoked = new AtomicBoolean();
    // when
    uut.doFilter(request, response, (req, res) -> invoked.set(true));
    // then
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(invoked).isFalse();
  }
}
