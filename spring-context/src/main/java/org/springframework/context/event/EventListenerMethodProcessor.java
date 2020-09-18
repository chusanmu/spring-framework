/*
 * Copyright 2002-2019 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * TODO: 实现SmartInitializingSingleton接口，会在preInstantiateSingletons()的最后一步执行
 * Registers {@link EventListener} methods as individual {@link ApplicationListener} instances.
 * Implements {@link BeanFactoryPostProcessor} (as of 5.1) primarily for early retrieval,
 * avoiding AOP checks for this processor bean and its {@link EventListenerFactory} delegates.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 * @see EventListenerFactory
 * @see DefaultEventListenerFactory
 */
public class EventListenerMethodProcessor
		implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {

	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private ConfigurableApplicationContext applicationContext;

	@Nullable
	private ConfigurableListableBeanFactory beanFactory;

	@Nullable
	private List<EventListenerFactory> eventListenerFactories;
	/**
	 * TODO: 解析注解中的condition的
	 */
	private final EventExpressionEvaluator evaluator = new EventExpressionEvaluator();
	/**
	 * TODO: 这样set也变成线程安全的了
	 */
	private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext,
				"ApplicationContext does not implement ConfigurableApplicationContext");
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	/**
	 * TODO: 这个方法是BeanFactoryPostProcessor的方法，它在容器的BeanFactory准备完成后，会执行此后置处理器
	 * @param beanFactory the bean factory used by the application context
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		// TODO: 此处作用，BeanFactory工厂准备好了之后，就去找所有的EventListenerFactory，然后保存起来
		// TODO: 默认情况下Spring在准备bean工厂的时候，会给我们注册一个DefaultEventListenerFactory, 如果使用了注解驱动的Spring事务如 EnableTransactionManagement, 它会额外再添加一个TransactionalEventListenerFactory
		Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
		List<EventListenerFactory> factories = new ArrayList<>(beans.values());
		// TODO: 根据Order进行排序
		AnnotationAwareOrderComparator.sort(factories);
		this.eventListenerFactories = factories;
	}


	@Override
	public void afterSingletonsInstantiated() {
		// TODO: 从容器里获得所有的EventListenerFactory, 它是用来后面处理标注了@EventListener方法的工厂，Spring默认放置的是DefaultEventListenerFactory，我们也可以继续往里放
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(this.beanFactory != null, "No ConfigurableListableBeanFactory set");
		// TODO: 用Object,拿到容器里面所有的Bean定义，一个一个的检查
		String[] beanNames = beanFactory.getBeanNamesForType(Object.class);
		for (String beanName : beanNames) {
			// TODO: 不处理Scope作用域代理的类
			if (!ScopedProxyUtils.isScopedTarget(beanName)) {
				Class<?> type = null;
				try {
					// TODO: 防止是代理，把真实的类型拿出来
					type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				if (type != null) {
					// TODO: 对专门的作用域进行兼容
					if (ScopedObject.class.isAssignableFrom(type)) {
						try {
							Class<?> targetClass = AutoProxyUtils.determineTargetClass(
									beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
							if (targetClass != null) {
								type = targetClass;
							}
						}
						catch (Throwable ex) {
							// An invalid scoped proxy arrangement - let's ignore it.
							if (logger.isDebugEnabled()) {
								logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
							}
						}
					}
					try {
						// TODO: 真正去处理这个bean里面的方法
						processBean(beanName, type);
					}
					catch (Throwable ex) {
						throw new BeanInitializationException("Failed to process @EventListener " +
								"annotation on bean with name '" + beanName + "'", ex);
					}
				}
			}
		}
	}

	private void processBean(final String beanName, final Class<?> targetType) {
		// TODO: 缓存下没有被注解过的class,这样再次解析此class就不用再处理了，这是为了加速父子容器的情况，做的特殊的优化
		if (!this.nonAnnotatedClasses.contains(targetType) &&
				!targetType.getName().startsWith("java") &&
				!isSpringContainerClass(targetType)) {

			Map<Method, EventListener> annotatedMethods = null;
			try {
				// TODO: 此方法的核心，就是找打这个class里面标注此注解的Methods们
				annotatedMethods = MethodIntrospector.selectMethods(targetType,
						(MethodIntrospector.MetadataLookup<EventListener>) method ->
								AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
			}
			catch (Throwable ex) {
				// An unresolvable type in a method signature, probably from a lazy bean - let's ignore it.
				if (logger.isDebugEnabled()) {
					logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
				}
			}
			// TODO: 若一个都没找到，那就标注此类没有标注注解，那就标注一下此类
			if (CollectionUtils.isEmpty(annotatedMethods)) {
				this.nonAnnotatedClasses.add(targetType);
				if (logger.isTraceEnabled()) {
					logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
				}
			}
			else {
				// Non-empty set of methods
				// TODO: 若存在对应的@EventListener标注的方法，那就走到这里，最终此method是交给EventListenerFactory这个工厂，适配成一个ApplicationListener的，适配类为ApplicationListenerMethodAdapter,它也是个ApplicationListener
				ConfigurableApplicationContext context = this.applicationContext;
				Assert.state(context != null, "No ApplicationContext set");
				List<EventListenerFactory> factories = this.eventListenerFactories;
				Assert.state(factories != null, "EventListenerFactory List not initialized");
				// TODO: 处理这些带有@EventListener注解的方法们
				for (Method method : annotatedMethods.keySet()) {
					// TODO: 拿到每个EventListenerFactory, 一般情况下只有DefaultEventListenerFactory，但是若是注解驱动的事务还会有TransactionalEventListenerFactory
					for (EventListenerFactory factory : factories) {
						// TODO: 默认spring给我们注册一个，是否支持去处理此方法，注意spring默认都是true
						if (factory.supportsMethod(method)) {
							// TODO: 把这个方法弄成一个可以执行的方法，主要和权限有关
							// TODO: 这里需要注意，若你是JDK代理，就不要在实现类里写@EventListener注解的监听器，否则会报错，CGLIB代理则没关系
							Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
							// TODO: 把这个方法包装成一个监听器ApplicationListener, 通过工厂创建出来的监听器，也给添加到context里面去
							ApplicationListener<?> applicationListener =
									factory.createApplicationListener(beanName, targetType, methodToUse);
							if (applicationListener instanceof ApplicationListenerMethodAdapter) {
								// TODO: init方法是把ApplicationContext注入进去
								((ApplicationListenerMethodAdapter) applicationListener).init(context, this.evaluator);
							}
							// TODO: 把当前listener加到容器里面去
							context.addApplicationListener(applicationListener);
							break;
						}
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" +
							beanName + "': " + annotatedMethods);
				}
			}
		}
	}

	/**
	 * Determine whether the given class is an {@code org.springframework}
	 * bean class that is not annotated as a user or test {@link Component}...
	 * which indicates that there is no {@link EventListener} to be found there.
	 * @since 5.1
	 */
	private static boolean isSpringContainerClass(Class<?> clazz) {
		return (clazz.getName().startsWith("org.springframework.") &&
				!AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class));
	}

}
