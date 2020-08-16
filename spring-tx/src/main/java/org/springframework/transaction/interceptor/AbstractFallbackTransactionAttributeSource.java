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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodClassKey;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Abstract implementation of {@link TransactionAttributeSource} that caches
 * attributes for methods and implements a fallback policy: 1. specific target
 * method; 2. target class; 3. declaring method; 4. declaring class/interface.
 *
 * <p>Defaults to using the target class's transaction attribute if none is
 * associated with the target method. Any transaction attribute associated with
 * the target method completely overrides a class transaction attribute.
 * If none found on the target class, the interface that the invoked method
 * has been called through (in case of a JDK proxy) will be checked.
 *
 * <p>This implementation caches attributes by method after they are first used.
 * If it is ever desirable to allow dynamic changing of transaction attributes
 * (which is very unlikely), caching could be made configurable. Caching is
 * desirable because of the cost of evaluating rollback rules.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public abstract class AbstractFallbackTransactionAttributeSource implements TransactionAttributeSource {

	/**
	 * TODO: 针对没有事务注解属性的方法进行事务注解属性缓存时 使用的特殊值，用于标记该方法没有事务注解属性
	 * TODO: 从而不用在首次缓存信息后，不用再次重复执行真正的分析，来提高查询的效率，标注了@Transactional注解的表示有事务属性的，才会最终加入事务，但是此处需要注意的是，只要被事务的advisor切中的，都会缓存起来，防止过度的查找
	 * Canonical value held in cache to indicate no transaction attribute was
	 * found for this method, and we don't need to look again.
	 */
	@SuppressWarnings("serial")
	private static final TransactionAttribute NULL_TRANSACTION_ATTRIBUTE = new DefaultTransactionAttribute() {
		@Override
		public String toString() {
			return "null";
		}
	};


	/**
	 * Logger available to subclasses.
	 * <p>As this base class is not marked Serializable, the logger will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * TODO: 方法上的事务注解属性缓存，key使用目标类上的方法，使用类型MethodClassKey来表示，这个Map会比较大，会被事务相关的advisor拦截下来的方法，最终都会缓存起来
	 * TODO: 因为会有很多，所以才需要 一个 NULL_TRANSACTION_ATTRIBUTE 常量来提高查找的效率
	 * Cache of TransactionAttributes, keyed by method on a specific target class.
	 * <p>As this base class is not marked Serializable, the cache will be recreated
	 * after serialization - provided that the concrete subclass is Serializable.
	 */
	private final Map<Object, TransactionAttribute> attributeCache = new ConcurrentHashMap<>(1024);


	/**
	 * TODO: 获取指定方法上的注解事务属性，如果方法上没有注解事务属性，则使用目标方法所属类上的注解事务属性
	 * Determine the transaction attribute for this method invocation.
	 * <p>Defaults to the class's transaction attribute if no method attribute is found.
	 * @param method the method for the current invocation (never {@code null})
	 * @param targetClass the target class for this invocation (may be {@code null})
	 * @return a TransactionAttribute for this method, or {@code null} if the method
	 * is not transactional
	 */
	@Override
	@Nullable
	public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// TODO: 如果目标方法是内置类Object上的方法，总是返回null, 这些方法上不应用事务
		if (method.getDeclaringClass() == Object.class) {
			return null;
		}

		// First, see if we have a cached value.
		// TODO: 构建cacheKey MethodClassKey 注意需要重写equals和hashCode
		Object cacheKey = getCacheKey(method, targetClass);
		TransactionAttribute cached = this.attributeCache.get(cacheKey);
		// TODO: 如果cache里面找到了
		if (cached != null) {
			// Value will either be canonical value indicating there is no transaction attribute,
			// or an actual transaction attribute.
			// TODO: 虽然找到了，但是根本不存在注解，缓存的是个null，则直接返回null吧
			if (cached == NULL_TRANSACTION_ATTRIBUTE) {
				return null;
			}
			else {
				return cached;
			}
		}
		// TODO: 缓存没有命中
		else {
			// We need to work it out.
			// TODO: 通过方法，目标class，分析出此方法上的事务属性
			TransactionAttribute txAttr = computeTransactionAttribute(method, targetClass);
			// Put it in the cache.
			// TODO: 如果目标方法上并没有使用注解等事务属性，缓存一个特殊值
			if (txAttr == null) {
				this.attributeCache.put(cacheKey, NULL_TRANSACTION_ATTRIBUTE);
			}
			else {
				// TODO: 存在目标属性，就put到里面去，拿到方法名称
				String methodIdentification = ClassUtils.getQualifiedMethodName(method, targetClass);
				if (txAttr instanceof DefaultTransactionAttribute) {
					// TODO: 设置了一个descriptor
					((DefaultTransactionAttribute) txAttr).setDescriptor(methodIdentification);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Adding transactional method '" + methodIdentification + "' with attribute: " + txAttr);
				}
				// TODO: 放到缓存里面去
				this.attributeCache.put(cacheKey, txAttr);
			}
			return txAttr;
		}
	}

	/**
	 * Determine a cache key for the given method and target class.
	 * <p>Must not produce same key for overloaded methods.
	 * Must produce same key for different instances of the same method.
	 * @param method the method (never {@code null})
	 * @param targetClass the target class (may be {@code null})
	 * @return the cache key (never {@code null})
	 */
	protected Object getCacheKey(Method method, @Nullable Class<?> targetClass) {
		return new MethodClassKey(method, targetClass);
	}

	/**
	 * TODO: 查找目标方法上的事务注解属性，一个核心方法
	 * Same signature as {@link #getTransactionAttribute}, but doesn't cache the result.
	 * {@link #getTransactionAttribute} is effectively a caching decorator for this method.
	 * <p>As of 4.1.8, this method can be overridden.
	 * @since 4.1.8
	 * @see #getTransactionAttribute
	 */
	@Nullable
	protected TransactionAttribute computeTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		// Don't allow no-public methods as required.
		// TODO: 如果事务注解属性分析仅仅针对public，而当前方法不是public，则直接返回null
		// TODO: 如果是private, aop是能切入，代理对象也会生成，但是事务不会生效的
		if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
			return null;
		}

		// The method may be on an interface, but we need attributes from the target class.
		// If the target class is null, the method will be unchanged.
		// TODO: 因为method并不一定属于目标类，所以这个方法就是获取targetClass上的那个和method对应的方法，也就是最终要执行的方法
		Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

		// First try is the method in the target class.
		// TODO: 第一步直接去查找标记在方法上的事务属性，如果方法上有就直接返回得了，不用再去看类上的了
		// TODO: findTransactionAttribute 子类去实现的
		TransactionAttribute txAttr = findTransactionAttribute(specificMethod);
		if (txAttr != null) {
			return txAttr;
		}

		// Second try is the transaction attribute on the target class.
		// TODO: 然后尝试检查事务注解属性是否标记在目标specificMethod所属类上
		txAttr = findTransactionAttribute(specificMethod.getDeclaringClass());
		if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
			return txAttr;
		}
		// TODO: 程序走到这里说明目标方法specificMethod，也就是实现类上的目标方法没有标记事务注解属性，否则就直接返回喽
		// TODO: 如果specificMethod和Method不同，则说明specificMethod是具体实现类上的方法method,是实现类所实现接口的方法，因此再次尝试从method上获取事务注解属性
		// TODO: 这也是为何@Transactional标记在接口上或者接口方法上都好使的原因
		if (specificMethod != method) {
			// Fallback is to look at the original method.
			txAttr = findTransactionAttribute(method);
			if (txAttr != null) {
				return txAttr;
			}
			// Last fallback is the class of the original method.
			txAttr = findTransactionAttribute(method.getDeclaringClass());
			if (txAttr != null && ClassUtils.isUserLevelMethod(method)) {
				return txAttr;
			}
		}

		return null;
	}


	/**
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given class, if any.
	 * @param clazz the class to retrieve the attribute for
	 * @return all transaction attribute associated with this class, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Class<?> clazz);

	/**
	 * Subclasses need to implement this to return the transaction attribute for the
	 * given method, if any.
	 * @param method the method to retrieve the attribute for
	 * @return all transaction attribute associated with this method, or {@code null} if none
	 */
	@Nullable
	protected abstract TransactionAttribute findTransactionAttribute(Method method);

	/**
	 * Should only public methods be allowed to have transactional semantics?
	 * <p>The default implementation returns {@code false}.
	 */
	protected boolean allowPublicMethodsOnly() {
		return false;
	}

}
