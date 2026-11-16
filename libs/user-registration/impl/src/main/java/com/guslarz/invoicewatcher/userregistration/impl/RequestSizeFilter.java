package com.guslarz.invoicewatcher.userregistration.impl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds JSON input including chunked bodies before deserialization; no request body is logged. */
@Component
public class RequestSizeFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/api/")
        && ("POST".equals(request.getMethod())
            || "PUT".equals(request.getMethod())
            || "DELETE".equals(request.getMethod()))) {
      byte[] body = request.getInputStream().readNBytes(32769);
      if (body.length > 32768) {
        response.sendError(413);
        return;
      }
      chain.doFilter(
          new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
              var input = new ByteArrayInputStream(body);
              return new ServletInputStream() {
                @Override
                public int read() {
                  return input.read();
                }

                @Override
                public boolean isFinished() {
                  return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                  return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                  throw new UnsupportedOperationException("Synchronous input only");
                }
              };
            }
          },
          response);
    } else {
      chain.doFilter(request, response);
    }
  }
}
