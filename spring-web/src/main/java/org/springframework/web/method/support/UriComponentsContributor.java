/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.web.method.support;

import java.util.Map;

import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TODO: 通过查看方法参数和参数值并决定更新url的哪部分内容，为构建uriComponents的策略接口
 * Strategy for contributing to the building of a {@link UriComponents} by
 * looking at a method parameter and an argument value and deciding what
 * part of the target URL should be updated.
 *
 * @author Oliver Gierke
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface UriComponentsContributor {

	/**
	 * TODO: 这个方法完全同handlerMethodArgumentResolver的这个方法
	 * Whether this contributor supports the given method parameter.
	 */
	boolean supportsParameter(MethodParameter parameter);

	/**
	 * TODO: 处理给定的方法参数，然后更新uriComponentbuilder，或者使用uri变量添加到映射中，以便在处理完所有参数后用于扩展uri
	 * Process the given method argument and either update the
	 * {@link UriComponentsBuilder} or add to the map with URI variables
	 * to use to expand the URI after all arguments are processed.
	 * @param parameter the controller method parameter (never {@code null})
	 * @param value the argument value (possibly {@code null})
	 * @param builder the builder to update (never {@code null})
	 * @param uriVariables a map to add URI variables to (never {@code null})
	 * @param conversionService a ConversionService to format values as Strings
	 */
	void contributeMethodArgument(MethodParameter parameter, Object value, UriComponentsBuilder builder,
			Map<String, Object> uriVariables, ConversionService conversionService);

}
