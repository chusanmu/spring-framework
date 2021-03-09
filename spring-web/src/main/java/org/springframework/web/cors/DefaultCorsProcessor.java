/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.cors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.WebUtils;

/**
 * The default implementation of {@link CorsProcessor}, as defined by the
 * <a href="https://www.w3.org/TR/cors/">CORS W3C recommendation</a>.
 *
 * <p>Note that when input {@link CorsConfiguration} is {@code null}, this
 * implementation does not reject simple or actual requests outright but simply
 * avoid adding CORS headers to the response. CORS processing is also skipped
 * if the response already contains CORS headers, or if the request is detected
 * as a same-origin one.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public class DefaultCorsProcessor implements CorsProcessor {

	private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);


	@Override
	@SuppressWarnings("resource")
	public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		// TODO: 如果不是跨域请求，不处理, 看请求头里有没有 origin
		// TODO: 如果有这个头就是跨域请求
		if (!CorsUtils.isCorsRequest(request)) {
			return true;
		}

		// TODO: 若响应头里已经设置好了 Access-Control-Allow-Origin这个响应头，此处理器就不管了
		ServletServerHttpResponse serverResponse = new ServletServerHttpResponse(response);
		if (responseHasCors(serverResponse)) {
			logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
			return true;
		}

		// TODO: 即使你有origin请求头，但是是同源的请求，那也不处理了
		ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
		if (WebUtils.isSameOrigin(serverRequest)) {
			logger.trace("Skip: request is from same origin");
			return true;
		}

		// TODO: 是否是预检请求，判断标准如下:
		// TODO: 是跨域请求 && 是`Options`请求，有 Access-Control-Request-Method这个请求头
		boolean preFlightRequest = CorsUtils.isPreFlightRequest(request);
		// TODO: 若config == null, 分两种case：是预检请求 但是没有给config, 那就拒绝，给出状态码 403
		if (config == null) {
			if (preFlightRequest) {
				rejectRequest(serverResponse);
				// TODO: 告诉后面的处理器不用再处理了
				return false;
			}
			else {
				// TODO: 虽然没给config，但是不是预检请求，是真实的请求，返回true
				return true;
			}
		}
		// TODO: 真正的跨域处理逻辑
		// TODO: 它的处理逻辑比较简单
		return handleInternal(serverRequest, serverResponse, config, preFlightRequest);
	}

	private boolean responseHasCors(ServerHttpResponse response) {
		try {
			return (response.getHeaders().getAccessControlAllowOrigin() != null);
		}
		catch (NullPointerException npe) {
			// SPR-11919 and https://issues.jboss.org/browse/WFLY-3474
			return false;
		}
	}

	/**
	 * Invoked when one of the CORS checks failed.
	 * The default implementation sets the response status to 403 and writes
	 * "Invalid CORS request" to the response.
	 */
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		response.setStatusCode(HttpStatus.FORBIDDEN);
		response.getBody().write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Handle the given request.
	 */
	protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
			CorsConfiguration config, boolean preFlightRequest) throws IOException {

		String requestOrigin = request.getHeaders().getOrigin();
		String allowOrigin = checkOrigin(config, requestOrigin);
		HttpHeaders responseHeaders = response.getHeaders();

		responseHeaders.addAll(HttpHeaders.VARY, Arrays.asList(HttpHeaders.ORIGIN,
				HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));

		if (allowOrigin == null) {
			logger.debug("Reject: '" + requestOrigin + "' origin is not allowed");
			rejectRequest(response);
			return false;
		}

		HttpMethod requestMethod = getMethodToUse(request, preFlightRequest);
		List<HttpMethod> allowMethods = checkMethods(config, requestMethod);
		if (allowMethods == null) {
			logger.debug("Reject: HTTP '" + requestMethod + "' is not allowed");
			rejectRequest(response);
			return false;
		}

		List<String> requestHeaders = getHeadersToUse(request, preFlightRequest);
		List<String> allowHeaders = checkHeaders(config, requestHeaders);
		if (preFlightRequest && allowHeaders == null) {
			logger.debug("Reject: headers '" + requestHeaders + "' are not allowed");
			rejectRequest(response);
			return false;
		}

		responseHeaders.setAccessControlAllowOrigin(allowOrigin);

		if (preFlightRequest) {
			responseHeaders.setAccessControlAllowMethods(allowMethods);
		}

		if (preFlightRequest && !allowHeaders.isEmpty()) {
			responseHeaders.setAccessControlAllowHeaders(allowHeaders);
		}

		if (!CollectionUtils.isEmpty(config.getExposedHeaders())) {
			responseHeaders.setAccessControlExposeHeaders(config.getExposedHeaders());
		}

		if (Boolean.TRUE.equals(config.getAllowCredentials())) {
			responseHeaders.setAccessControlAllowCredentials(true);
		}

		if (preFlightRequest && config.getMaxAge() != null) {
			responseHeaders.setAccessControlMaxAge(config.getMaxAge());
		}

		response.flush();
		return true;
	}

	/**
	 * Check the origin and determine the origin for the response. The default
	 * implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected String checkOrigin(CorsConfiguration config, @Nullable String requestOrigin) {
		return config.checkOrigin(requestOrigin);
	}

	/**
	 * Check the HTTP method and determine the methods for the response of a
	 * pre-flight request. The default implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkHttpMethod(HttpMethod)}.
	 */
	@Nullable
	protected List<HttpMethod> checkMethods(CorsConfiguration config, @Nullable HttpMethod requestMethod) {
		return config.checkHttpMethod(requestMethod);
	}

	@Nullable
	private HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
		return (isPreFlight ? request.getHeaders().getAccessControlRequestMethod() : request.getMethod());
	}

	/**
	 * Check the headers and determine the headers for the response of a
	 * pre-flight request. The default implementation simply delegates to
	 * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
	 */
	@Nullable
	protected List<String> checkHeaders(CorsConfiguration config, List<String> requestHeaders) {
		return config.checkHeaders(requestHeaders);
	}

	private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
		HttpHeaders headers = request.getHeaders();
		return (isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.keySet()));
	}

}
