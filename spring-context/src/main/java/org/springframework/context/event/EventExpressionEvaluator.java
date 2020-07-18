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

package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.lang.Nullable;

/**
 * TODO: 处理eventCondition
 * Utility class handling the SpEL expression parsing. Meant to be used
 * as a reusable, thread-safe component.
 *
 * @author Stephane Nicoll
 * @since 4.2
 * @see CachedExpressionEvaluator
 */
class EventExpressionEvaluator extends CachedExpressionEvaluator {

	/**
	 * TODO: ExpressionKey 为cachedExpressionEvaluator的一个内部类
	 * TODO: Expression为表达式
	 */
	private final Map<ExpressionKey, Expression> conditionCache = new ConcurrentHashMap<>(64);


	/**
	 * TODO: 只有这一个重要方法，EventExpressionRootObject就是简单的持有传入的两个变量的引用而已
	 * TODO: root是#root值的来源
	 * Specify if the condition defined by the specified expression matches.
	 */
	public boolean condition(String conditionExpression, ApplicationEvent event, Method targetMethod,
			AnnotatedElementKey methodKey, Object[] args, @Nullable BeanFactory beanFactory) {

		EventExpressionRootObject root = new EventExpressionRootObject(event, args);
		// TODO: 准备一个执行上下文，getParameterNameDiscoverer可以根据方法参数列表的名称取值，同时也支持a0,a1,a2等等都直接取值
		MethodBasedEvaluationContext evaluationContext = new MethodBasedEvaluationContext(
				root, targetMethod, args, getParameterNameDiscoverer());
		if (beanFactory != null) {
			evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
		}
		// TODO: 默认采用的是spelExpressionParser这个解析器解析这个表达式
		return (Boolean.TRUE.equals(getExpression(this.conditionCache, methodKey, conditionExpression).getValue(
				evaluationContext, Boolean.class)));
	}

}
