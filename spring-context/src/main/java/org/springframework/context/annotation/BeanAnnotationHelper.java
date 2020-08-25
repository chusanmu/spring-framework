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

package org.springframework.context.annotation;

import java.lang.reflect.Method;
import java.util.Map;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * Utilities for processing {@link Bean}-annotated methods.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
abstract class BeanAnnotationHelper {

	private static final Map<Method, String> beanNameCache = new ConcurrentReferenceHashMap<>();

	private static final Map<Method, Boolean> scopedProxyCache = new ConcurrentReferenceHashMap<>();


	public static boolean isBeanAnnotated(Method method) {
		return AnnotatedElementUtils.hasAnnotation(method, Bean.class);
	}

	public static String determineBeanNameFor(Method beanMethod) {
		// TODO: 看看缓存里面有没有
		String beanName = beanNameCache.get(beanMethod);
		if (beanName == null) {
			// By default, the bean name is the name of the @Bean-annotated method
			// TODO: 默认就是方法名
			beanName = beanMethod.getName();
			// Check to see if the user has explicitly set a custom bean name...
			// TODO: 去check下，看看用户自己有没有设置bean的名称，如果有@Bean注解，就去get一下
			AnnotationAttributes bean =
					AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Bean.class, false, false);
			if (bean != null) {
				// TODO: 把bean的name拿到，如果names的个数大于0，取用户自己设置的beanName
				String[] names = bean.getStringArray("name");
				if (names.length > 0) {
					beanName = names[0];
				}
			}
			// TODO: 缓存起来
			beanNameCache.put(beanMethod, beanName);
		}
		return beanName;
	}

	public static boolean isScopedProxy(Method beanMethod) {
		// TODO: 查看用户是否启动了 作用域代理
		Boolean scopedProxy = scopedProxyCache.get(beanMethod);
		if (scopedProxy == null) {
			AnnotationAttributes scope =
					AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Scope.class, false, false);
			scopedProxy = (scope != null && scope.getEnum("proxyMode") != ScopedProxyMode.NO);
			scopedProxyCache.put(beanMethod, scopedProxy);
		}
		return scopedProxy;
	}

}
