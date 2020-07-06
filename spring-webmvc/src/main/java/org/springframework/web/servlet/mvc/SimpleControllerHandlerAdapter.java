/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.web.servlet.mvc;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;

/**
 * TODO: 适配org.springframework.web.servlet.mvc.Controller 这种Handler, 它是一个非常古老的适配器，现在几乎已经启用
 * 它没有对参数的自动封装，校验等一系列高级功能，但是它保留有对ModelAndView的处理能力，这是区别于servlet这种处理器的地方
 *
 * @Controller("/test")
 * Adapter to use the plain {@link Controller} workflow interface with
 * the generic {@link org.springframework.web.servlet.DispatcherServlet}.
 * Supports handlers that implement the {@link LastModified} interface.
 *
 * <p>This is an SPI class, not used directly by application code.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see org.springframework.web.servlet.DispatcherServlet
 * @see Controller
 * @see LastModified
 * @see HttpRequestHandlerAdapter
 */
public class SimpleControllerHandlerAdapter implements HandlerAdapter {

	/**
	 * TODO: 支持实现了controller接口的这种handler
	 * @param handler handler object to check
	 * @return
	 */
	@Override
	public boolean supports(Object handler) {
		return (handler instanceof Controller);
	}

	/**
	 * TODO: dispatcherServlet会调用的，直接就调用我们实现了的那个方法. 这里可以看出来，直接就是操作的原生的httpServletRequest, 和 httpServletResponse.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler handler to use. This object must have previously been passed
	 * to the {@code supports} method of this interface, which must have
	 * returned {@code true}.
	 * @return
	 * @throws Exception
	 */
	@Override
	@Nullable
	public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		return ((Controller) handler).handleRequest(request, response);
	}

	/**
	 * 如果实现了LastModified接口，就调用，否则返回-1
	 * @param request current HTTP request
	 * @param handler handler to use
	 * @return
	 */
	@Override
	public long getLastModified(HttpServletRequest request, Object handler) {
		if (handler instanceof LastModified) {
			return ((LastModified) handler).getLastModified(request);
		}
		return -1L;
	}

}
