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

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

/**
 * TODO: 获取匹配 targetClass与Method的所有切面的通知
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	/**
	 * TODO: 根据method和targetClass，来从advised中获取 具体方法的执行链
	 * @param config the AOP configuration in the form of an Advised object
	 * @param method the proxied method
	 * @param targetClass the target class (may be {@code null} to indicate a proxy without
	 * target object, in which case the method's declaring class is the next best option)
	 * @return
	 */
	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		// TODO: 得到适配器注册器，主要作用是进行适配转换
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
		// TODO: 拿到adviced中的所有的advisor
		Advisor[] advisors = config.getAdvisors();
		List<Object> interceptorList = new ArrayList<>(advisors.length);
		// TODO: 拿到它的targetClass，注意它的targetClass有可能为代理类class
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		Boolean hasIntroductions = null;
		// TODO: 遍历所有的advisors，其目的是，找到所有能作用到method和targetClass上面的interceptor
		for (Advisor advisor : advisors) {
			// TODO: advisor 如果是一个PointcutAdvisor类型的
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				// TODO: 开始强制类型转换
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// TODO: 这里isPreFiltered默认false ，如果它为true,就不会去判断是否匹配类了，会判断classFilter是否匹配 actualClass
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					// TODO: 取出来方法匹配器
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					boolean match;
					if (mm instanceof IntroductionAwareMethodMatcher) {
						if (hasIntroductions == null) {
							// TODO: 判断类是否 可以引介增强
							hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
						}
						// TODO: 去匹配方法, hasIntroductions 是否存在引介增强接口
						match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
					}
					else {
						// TODO: 大部分情况都是走的这里
						match = mm.matches(method, actualClass);
					}
					if (match) {
						// TODO: 通过对适配器将通知advice 包装成MethodInterceptor
						MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
						// TODO: 是否需要动态匹配，动态匹配，会匹配入参
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							// TODO: 如果需要在运行时动态拦截方法的执行则创建一个简单的对象封装相关的数据，他将延时到方法执行的时候验证要不要执行此通知
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							// TODO: 否则，静态匹配，直接把符合条件的interceptors加到interceptorList 里面
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			// TODO: 如果advisor是一个引介增强
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				// TODO: 强转之后，进行匹配
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					// TODO: 匹配成功，把所有的interceptor拿到，然后加到interceptorList中
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				// TODO: 如果以上两种都不是，则把当前advisor进行适配，如果适配返回了Interceptor，则加到interceptorList中
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}
		// TODO: 最后返回所有符合条件的interceptor集合
		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
		// TODO: 遍历所有的advisors 看看是否有引介增强，如果有，判断类是否符合引介增强的条件
		for (Advisor advisor : advisors) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				// TODO: 是否符合引介增强，如果符合条件，则返回true
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
