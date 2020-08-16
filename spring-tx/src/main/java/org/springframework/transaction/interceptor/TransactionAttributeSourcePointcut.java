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

package org.springframework.transaction.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ObjectUtils;

/**
 * TODO: 这个就是事务的匹配pointcut切面，决定了哪些类需要生成代理对象，从而应用事务，它默认是default的，也就是给内部使用的
 * TODO: 继承了StaticMethodMatcherPointcut， 所以classFilter为true, 同时 isRuntime=false, 表示只需要对方法进行静态匹配即可
 * Inner class that implements a Pointcut that matches if the underlying
 * {@link TransactionAttributeSource} has an attribute for a given method.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 */
@SuppressWarnings("serial")
abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

	/**
	 * TODO: 方法的匹配，静态匹配即可，因为事务无需动态匹配那么细粒度
	 * @param method the candidate method
	 * @param targetClass the target class
	 * @return
	 */
	@Override
	public boolean matches(Method method, Class<?> targetClass) {
		// TODO: 如果实现了如下三个接口的子类，那就不需要被代理了，直接放行
		// TODO: TransactionProxy它是springProxy的子类，如果是被TransactionProxyFactoryBean生产出来的bean，就会自动实现此接口，那么就不会被这里再次代理了，
		// TODO: PlatformTransactionManager Spring抽象的事务管理器
		// TODO: PersistenceExceptionTranslator 对 RuntimeException转换成DataAccessException的转换接口
		if (TransactionalProxy.class.isAssignableFrom(targetClass) ||
				PlatformTransactionManager.class.isAssignableFrom(targetClass) ||
				PersistenceExceptionTranslator.class.isAssignableFrom(targetClass)) {
			return false;
		}
		// TODO: 重要，拿到事务属性源，如果tas为空，tas == null 表示没有配置事务属性源，那就全部匹配，也就是说所有的方法都匹配。。。。
		// TODO: 或者标注了@Transactional这个注解才会给匹配
		TransactionAttributeSource tas = getTransactionAttributeSource();
		return (tas == null || tas.getTransactionAttribute(method, targetClass) != null);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof TransactionAttributeSourcePointcut)) {
			return false;
		}
		TransactionAttributeSourcePointcut otherPc = (TransactionAttributeSourcePointcut) other;
		return ObjectUtils.nullSafeEquals(getTransactionAttributeSource(), otherPc.getTransactionAttributeSource());
	}

	@Override
	public int hashCode() {
		return TransactionAttributeSourcePointcut.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + getTransactionAttributeSource();
	}


	/**
	 * Obtain the underlying TransactionAttributeSource (may be {@code null}).
	 * To be implemented by subclasses.
	 */
	@Nullable
	protected abstract TransactionAttributeSource getTransactionAttributeSource();

}
