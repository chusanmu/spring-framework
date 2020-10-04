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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;

/**
 * Base class for {@link BeanPostProcessor} implementations that apply a
 * Spring AOP {@link Advisor} to specific beans.
 *
 * @author Juergen Hoeller
 * @since 3.2
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisingBeanPostProcessor extends ProxyProcessorSupport implements BeanPostProcessor {

	@Nullable
	protected Advisor advisor;

	/**
	 * 在之前是否存在advisors
	 */
	protected boolean beforeExistingAdvisors = false;

	/**
	 * TODO: 缓存 缓存这个 class是否符合增强条件
	 */
	private final Map<Class<?>, Boolean> eligibleBeans = new ConcurrentHashMap<>(256);


	/**
	 * Set whether this post-processor's advisor is supposed to apply before
	 * existing advisors when encountering a pre-advised object.
	 * <p>Default is "false", applying the advisor after existing advisors, i.e.
	 * as close as possible to the target method. Switch this to "true" in order
	 * for this post-processor's advisor to wrap existing advisors as well.
	 * <p>Note: Check the concrete post-processor's javadoc whether it possibly
	 * changes this flag by default, depending on the nature of its advisor.
	 */
	public void setBeforeExistingAdvisors(boolean beforeExistingAdvisors) {
		this.beforeExistingAdvisors = beforeExistingAdvisors;
	}


	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		// TODO: 如果没有advisor, 或者是个aop基础组件类 直接把这个bean返回
		if (this.advisor == null || bean instanceof AopInfrastructureBean) {
			// Ignore AOP infrastructure such as scoped proxies.
			return bean;
		}

		// TODO: 如果这个bean已经被增强过了
		if (bean instanceof Advised) {
			Advised advised = (Advised) bean;
			// TODO: 主要是判断这个bean是否合法，是否应该被增强
			if (!advised.isFrozen() && isEligible(AopUtils.getTargetClass(bean))) {
				// Add our local Advisor to the existing proxy's Advisor chain...
				// TODO: 这个值默认是false, 可以设置为true, 表示直接把advisor增强放到第一个
				// TODO: 异步async增强的时候，将此值设置为了true, 会把async增强放到第一位，原因是，比如 @Async 和 @Transaction一起使用的时候，肯定是@Async先生效，然后事务再生效，如果事务增强器先生效，然后再开启一个线程的话，显然事务就无效了
				if (this.beforeExistingAdvisors) {
					advised.addAdvisor(0, this.advisor);
				}
				else {
					// TODO: 把advisor直接加到尾部
					advised.addAdvisor(this.advisor);
				}
				return bean;
			}
		}
		// TODO: 如果这个bean还没有被增强过
		if (isEligible(bean, beanName)) {
			// TODO: 准备ProxyFactory，用来产生代理对象
			ProxyFactory proxyFactory = prepareProxyFactory(bean, beanName);
			// TODO: 判断是否直接对target class进行代理
			if (!proxyFactory.isProxyTargetClass()) {
				// TODO: 如果不是的话，则会尝试使用jdk的动态代理，这时候会去计算代理接口
				evaluateProxyInterfaces(bean.getClass(), proxyFactory);
			}
			// TODO: proxyFactory设置advisor
			proxyFactory.addAdvisor(this.advisor);
			customizeProxyFactory(proxyFactory);
			// TODO: 获取代理对象
			return proxyFactory.getProxy(getProxyClassLoader());
		}

		// No proxy needed.
		// TODO: 不需要代理，直接返回原始bean
		return bean;
	}

	/**
	 * Check whether the given bean is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Delegates to {@link #isEligible(Class)} for target class checking.
	 * Can be overridden e.g. to specifically exclude certain beans by name.
	 * <p>Note: Only called for regular bean instances but not for existing
	 * proxy instances which implement {@link Advised} and allow for adding
	 * the local {@link Advisor} to the existing proxy's {@link Advisor} chain.
	 * For the latter, {@link #isEligible(Class)} is being called directly,
	 * with the actual target class behind the existing proxy (as determined
	 * by {@link AopUtils#getTargetClass(Object)}).
	 * @param bean the bean instance
	 * @param beanName the name of the bean
	 * @see #isEligible(Class)
	 */
	protected boolean isEligible(Object bean, String beanName) {
		return isEligible(bean.getClass());
	}

	/**
	 * TODO: 检查给定的class 是否应该被增强
	 *
	 * Check whether the given class is eligible for advising with this
	 * post-processor's {@link Advisor}.
	 * <p>Implements caching of {@code canApply} results per bean target class.
	 * @param targetClass the class to check against
	 * @see AopUtils#canApply(Advisor, Class)
	 */
	protected boolean isEligible(Class<?> targetClass) {
		// TODO: 如果缓存里缓存了，表示已经检查过了，直接把缓存里的值拿出来返回回去。
		Boolean eligible = this.eligibleBeans.get(targetClass);
		if (eligible != null) {
			return eligible;
		}
		// TODO: 如果advisor 为空，也直接返回
		if (this.advisor == null) {
			return false;
		}
		// TODO: 使用AopUtils.canApply判断这个targetClass是否符合增强条件
		eligible = AopUtils.canApply(this.advisor, targetClass);
		// TODO: 然后缓存起来
		this.eligibleBeans.put(targetClass, eligible);
		// TODO: 最后直接return
		return eligible;
	}

	/**
	 * TODO: 准备一个ProxyFactory
	 *
	 * Prepare a {@link ProxyFactory} for the given bean.
	 * <p>Subclasses may customize the handling of the target instance and in
	 * particular the exposure of the target class. The default introspection
	 * of interfaces for non-target-class proxies and the configured advisor
	 * will be applied afterwards; {@link #customizeProxyFactory} allows for
	 * late customizations of those parts right before proxy creation.
	 * @param bean the bean instance to create a proxy for
	 * @param beanName the corresponding bean name
	 * @return the ProxyFactory, initialized with this processor's
	 * {@link ProxyConfig} settings and the specified bean
	 * @since 4.2.3
	 * @see #customizeProxyFactory
	 */
	protected ProxyFactory prepareProxyFactory(Object bean, String beanName) {
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.copyFrom(this);
		proxyFactory.setTarget(bean);
		return proxyFactory;
	}

	/**
	 * Subclasses may choose to implement this: for example,
	 * to change the interfaces exposed.
	 * <p>The default implementation is empty.
	 * @param proxyFactory the ProxyFactory that is already configured with
	 * target, advisor and interfaces and will be used to create the proxy
	 * immediately after this method returns
	 * @since 4.2.3
	 * @see #prepareProxyFactory
	 */
	protected void customizeProxyFactory(ProxyFactory proxyFactory) {
	}

}
