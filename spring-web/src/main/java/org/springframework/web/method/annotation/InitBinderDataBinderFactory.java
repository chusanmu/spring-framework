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

package org.springframework.web.method.annotation;

import java.util.Collections;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.InvocableHandlerMethod;

/**
 * TODO: 主要用于处理标注有InitBinder的方法做初始绑定
 * Adds initialization to a WebDataBinder via {@code @InitBinder} methods.
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 */
public class InitBinderDataBinderFactory extends DefaultDataBinderFactory {

	/**
	 * TODO: 保存所有，@InitBinder可以标注N多个方法，所以此处是个List
	 */
	private final List<InvocableHandlerMethod> binderMethods;


	/**
	 * 此子类的唯一构造函数
	 * Create a new InitBinderDataBinderFactory instance.
	 * @param binderMethods {@code @InitBinder} methods
	 * @param initializer for global data binder initialization
	 */
	public InitBinderDataBinderFactory(@Nullable List<InvocableHandlerMethod> binderMethods,
			@Nullable WebBindingInitializer initializer) {

		super(initializer);
		this.binderMethods = (binderMethods != null ? binderMethods : Collections.emptyList());
	}


	/**
	 * Initialize a WebDataBinder with {@code @InitBinder} methods.
	 * <p>If the {@code @InitBinder} annotation specifies attributes names,
	 * it is invoked only if the names include the target object name.
	 * @throws Exception if one of the invoked @{@link InitBinder} methods fails
	 * @see #isBinderMethodApplicable
	 */
	@Override
	public void initBinder(WebDataBinder dataBinder, NativeWebRequest request) throws Exception {
		for (InvocableHandlerMethod binderMethod : this.binderMethods) {
			// TODO: 判断@InitBinder是否对dataBinder持有的target对象生效
			if (isBinderMethodApplicable(binderMethod, dataBinder)) {
				// TODO: 这里和调用普通控制器方法一样，方法入参上也可以写各式各样的参数
				Object returnValue = binderMethod.invokeForRequest(request, null, dataBinder);
				// TODO: 标记有@InitBinder注解的方法必须返回void
				if (returnValue != null) {
					throw new IllegalStateException(
							"@InitBinder methods must not return a value (should be void): " + binderMethod);
				}
			}
		}
	}

	/**
	 * Determine whether the given {@code @InitBinder} method should be used
	 * to initialize the given {@link WebDataBinder} instance. By default we
	 * check the specified attribute names in the annotation value, if any.
	 */
	protected boolean isBinderMethodApplicable(HandlerMethod initBinderMethod, WebDataBinder dataBinder) {
		// TODO: 直接查找注解啊
		InitBinder ann = initBinderMethod.getMethodAnnotation(InitBinder.class);
		Assert.state(ann != null, "No InitBinder annotation");
		String[] names = ann.value();
		return (ObjectUtils.isEmpty(names) || ObjectUtils.containsElement(names, dataBinder.getObjectName()));
	}

}
