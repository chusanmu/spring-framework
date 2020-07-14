/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.accept;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * TODO: 从名字可以看出 扩展名来自于param参数， 默认是没有开启的
 * Strategy that resolves the requested content type from a query parameter.
 * The default query parameter name is {@literal "format"}.
 *
 * <p>You can register static mappings between keys (i.e. the expected value of
 * the query parameter) and MediaType's via {@link #addMapping(String, MediaType)}.
 * As of 5.0 this strategy also supports dynamic lookups of keys via
 * {@link org.springframework.http.MediaTypeFactory#getMediaType}.
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public class ParameterContentNegotiationStrategy extends AbstractMappingContentNegotiationStrategy {
	/**
	 * TODO: 请求参数默认的key是format, 是可以设置和更改的 set方法
	 */
	private String parameterName = "format";


	/**
	 * 唯一构造
	 * Create an instance with the given map of file extensions and media types.
	 */
	public ParameterContentNegotiationStrategy(Map<String, MediaType> mediaTypes) {
		super(mediaTypes);
	}


	/**
	 * Set the name of the parameter to use to determine requested media types.
	 * <p>By default this is set to {@code "format"}.
	 */
	public void setParameterName(String parameterName) {
		Assert.notNull(parameterName, "'parameterName' is required");
		this.parameterName = parameterName;
	}
	public String getParameterName() {
		return this.parameterName;
	}


	@Override
	@Nullable
	protected String getMediaTypeKey(NativeWebRequest request) {
		// TODO: 调用getParameterName拿到 key format字段
		return request.getParameter(getParameterName());
	}

}
