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

package org.springframework.beans.factory.annotation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * TODO: 这个类挺重要的，处理了PostConstruct 和 PreDestroy 这两个注解
 *
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that invokes annotated init and destroy methods. Allows for an annotation
 * alternative to Spring's {@link org.springframework.beans.factory.InitializingBean}
 * and {@link org.springframework.beans.factory.DisposableBean} callback interfaces.
 *
 * <p>The actual annotation types that this post-processor checks for can be
 * configured through the {@link #setInitAnnotationType "initAnnotationType"}
 * and {@link #setDestroyAnnotationType "destroyAnnotationType"} properties.
 * Any custom annotation can be used, since there are no required annotation
 * attributes.
 *
 * <p>Init and destroy annotations may be applied to methods of any visibility:
 * public, package-protected, protected, or private. Multiple such methods
 * may be annotated, but it is recommended to only annotate one single
 * init method and destroy method, respectively.
 *
 * <p>Spring's {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor}
 * supports the JSR-250 {@link javax.annotation.PostConstruct} and {@link javax.annotation.PreDestroy}
 * annotations out of the box, as init annotation and destroy annotation, respectively.
 * Furthermore, it also supports the {@link javax.annotation.Resource} annotation
 * for annotation-driven injection of named beans.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #setInitAnnotationType
 * @see #setDestroyAnnotationType
 */
@SuppressWarnings("serial")
public class InitDestroyAnnotationBeanPostProcessor
		implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, PriorityOrdered, Serializable {

	protected transient Log logger = LogFactory.getLog(getClass());

	/**
	 * TODO: 标记初始化的注解 其实就是 @PostConstruct
	 */
	@Nullable
	private Class<? extends Annotation> initAnnotationType;

	/**
	 * TODO: 标记销毁的注解，其实就是 @PreDestroy
	 */
	@Nullable
	private Class<? extends Annotation> destroyAnnotationType;

	/**
	 * TODO: 顺序是最低的
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;

	/**
	 * TODO: 一个缓存，缓存了一个class对应的LifecycleMetadata
	 */
	@Nullable
	private final transient Map<Class<?>, LifecycleMetadata> lifecycleMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * TODO: 这里其实就是设置了初始化注解为 PostConstruct
	 *
	 * Specify the init annotation to check for, indicating initialization
	 * methods to call after configuration of a bean.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the JSR-250 {@link javax.annotation.PostConstruct} annotation.
	 */
	public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
		this.initAnnotationType = initAnnotationType;
	}

	/**
	 * TODO:  销毁注解为 PreDestroy
	 *
	 * Specify the destroy annotation to check for, indicating destruction
	 * methods to call when the context is shutting down.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the JSR-250 {@link javax.annotation.PreDestroy} annotation.
	 */
	public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
		this.destroyAnnotationType = destroyAnnotationType;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		// TODO: 根据class去构建它的 LifecycleMetadata
		LifecycleMetadata metadata = findLifecycleMetadata(beanType);
		// TODO: 对beanDefinition进行操作设置，设置initMethods和destroyMethods
		metadata.checkConfigMembers(beanDefinition);
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		// TODO: 查找这个bean对应的LifecycleMetadata
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			// TODO: 到这里 就去执行它的初始化方法
			metadata.invokeInitMethods(bean, beanName);
		}
		// TODO: 执行出错了，就直接抛出异常了，这地方要注意
		catch (InvocationTargetException ex) {
			throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * TODO: 在bean销毁之前会被调用
	 *
	 * @param bean the bean instance to be destroyed
	 * @param beanName the name of the bean
	 * @throws BeansException
	 */
	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
		// TODO: 查找这个bean对应的LifecycleMetadata
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			// TODO: 然后就去执行它的destroyMethods
			metadata.invokeDestroyMethods(bean, beanName);
		}
		// TODO: 如果抛出异常，这地方就给你自动catch住了
		catch (InvocationTargetException ex) {
			String msg = "Destroy method on bean with name '" + beanName + "' threw an exception";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex.getTargetException());
			}
			else {
				logger.warn(msg + ": " + ex.getTargetException());
			}
		}
		catch (Throwable ex) {
			logger.warn("Failed to invoke destroy method on bean with name '" + beanName + "'", ex);
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		// TODO: 通过 LifecycleMetadata 去判断，当前bean是否有销毁方法
		return findLifecycleMetadata(bean.getClass()).hasDestroyMethods();
	}


	/**
	 * TODO: 根据方法名 直译过来就是，查找一个class的 生命周期元数据
	 * @param clazz
	 * @return
	 */
	private LifecycleMetadata findLifecycleMetadata(Class<?> clazz) {
		// TODO: 如果缓存为空，去构建缓存
		if (this.lifecycleMetadataCache == null) {
			// Happens after deserialization, during destruction...
			return buildLifecycleMetadata(clazz);
		}
		// Quick check on the concurrent map first, with minimal locking.
		// TODO: 直接把当前class的信息 从缓存中拿到
		LifecycleMetadata metadata = this.lifecycleMetadataCache.get(clazz);
		// TODO: 如果metadata为空， 这时候会去 双重判断 + synchronized 然后去构建这个class的生命周期元信息，然后放进缓存中
		if (metadata == null) {
			synchronized (this.lifecycleMetadataCache) {
				metadata = this.lifecycleMetadataCache.get(clazz);
				if (metadata == null) {
					metadata = buildLifecycleMetadata(clazz);
					this.lifecycleMetadataCache.put(clazz, metadata);
				}
				return metadata;
			}
		}
		return metadata;
	}

	/**
	 * 构建一个class的元信息
	 *
	 * @param clazz
	 * @return
	 */
	private LifecycleMetadata buildLifecycleMetadata(final Class<?> clazz) {
		List<LifecycleElement> initMethods = new ArrayList<>();
		List<LifecycleElement> destroyMethods = new ArrayList<>();
		Class<?> targetClass = clazz;

		do {
			final List<LifecycleElement> currInitMethods = new ArrayList<>();
			final List<LifecycleElement> currDestroyMethods = new ArrayList<>();
			// TODO: 如果这个class里面有包含的 init method或者有destroy method，全部找出来, 通过list可以看到，是可以存在多个 @PreDestroy 这种方法的
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
					LifecycleElement element = new LifecycleElement(method);
					currInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Found init method on class [" + clazz.getName() + "]: " + method);
					}
				}
				if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
					currDestroyMethods.add(new LifecycleElement(method));
					if (logger.isTraceEnabled()) {
						logger.trace("Found destroy method on class [" + clazz.getName() + "]: " + method);
					}
				}
			});
			// TODO: 把找到的currInitMethods 放到第一个位置，这样做的好处是，保证父类先初始化
			initMethods.addAll(0, currInitMethods);
			destroyMethods.addAll(currDestroyMethods);
			// TODO: 接着找它的父类
			targetClass = targetClass.getSuperclass();
		}
		// TODO: 如果targetClass不为空，并且 targetClass不是Object类，就接着往下找
		while (targetClass != null && targetClass != Object.class);

		// TODO: 最后创建一个 LifecycleMetadata 返回回去, 如果当前class没有初始化方法 或者 destroy方法，那么其两个集合就是空的
		return new LifecycleMetadata(clazz, initMethods, destroyMethods);
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.logger = LogFactory.getLog(getClass());
	}


	/**
	 * Class representing information about annotated init and destroy methods.
	 */
	private class LifecycleMetadata {

		/**
		 * TODO: 内部维护的目标class
		 */
		private final Class<?> targetClass;

		/**
		 * TODO: 所有的initMethod
		 */
		private final Collection<LifecycleElement> initMethods;

		/**
		 * TODO: 所有的destroyMethods
		 */
		private final Collection<LifecycleElement> destroyMethods;

		/* ---------------- 已经在beanDefinition中标记过了的init和destroy methods -------------- */

		@Nullable
		private volatile Set<LifecycleElement> checkedInitMethods;

		@Nullable
		private volatile Set<LifecycleElement> checkedDestroyMethods;

		public LifecycleMetadata(Class<?> targetClass, Collection<LifecycleElement> initMethods,
				Collection<LifecycleElement> destroyMethods) {

			this.targetClass = targetClass;
			this.initMethods = initMethods;
			this.destroyMethods = destroyMethods;
		}

		/**
		 * TODO: 这里其实是对beanDefinition进行操作，其实就是把initMethod和destroyMethod放到beanDefinition里面记录下来，然后之后会用到
		 *
		 * @param beanDefinition
		 */
		public void checkConfigMembers(RootBeanDefinition beanDefinition) {
			Set<LifecycleElement> checkedInitMethods = new LinkedHashSet<>(this.initMethods.size());
			// TODO: 把所有的initMethods进行遍历
			for (LifecycleElement element : this.initMethods) {
				String methodIdentifier = element.getIdentifier();
				// TODO: 判断这个beanDefinition有没有设置过，没有设置过 才能进行设置
				if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
					// TODO: 这里就进行注册方法了，最后加到checkedInitMethods里面去
					beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
					checkedInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered init method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			// TODO: 对destroyMethods进行遍历
			Set<LifecycleElement> checkedDestroyMethods = new LinkedHashSet<>(this.destroyMethods.size());
			for (LifecycleElement element : this.destroyMethods) {
				String methodIdentifier = element.getIdentifier();
				// TODO: 没有设置过，才能进行注册
				if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
					beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
					checkedDestroyMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered destroy method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			// TODO: 最后把注册完了的方法 赋值给这俩集合
			this.checkedInitMethods = checkedInitMethods;
			this.checkedDestroyMethods = checkedDestroyMethods;
		}

		/**
		 * TODO: 执行初始化方法，其实就是执行@PostConstruct标记的方法
		 *
		 * @param target
		 * @param beanName
		 * @throws Throwable
		 */
		public void invokeInitMethods(Object target, String beanName) throws Throwable {
			Collection<LifecycleElement> checkedInitMethods = this.checkedInitMethods;
			Collection<LifecycleElement> initMethodsToIterate =
					(checkedInitMethods != null ? checkedInitMethods : this.initMethods);
			// TODO: 肯定是存在initMethods才会去处理，执行
			if (!initMethodsToIterate.isEmpty()) {
				// TODO: 然后进行遍历，一个个的进行执行
				for (LifecycleElement element : initMethodsToIterate) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking init method on bean '" + beanName + "': " + element.getMethod());
					}
					// TODO: 去执行方法了
					element.invoke(target);
				}
			}
		}

		/**
		 * TODO: 执行销毁方法，其实就是执行@PreDestroy标记的方法
		 *
		 * @param target
		 * @param beanName
		 * @throws Throwable
		 */
		public void invokeDestroyMethods(Object target, String beanName) throws Throwable {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			// TODO: 同样的，存在这个destroyMethod，然后才能去执行
			if (!destroyMethodsToUse.isEmpty()) {
				// TODO: 遍历一个个的去执行，说明 @PostConstruct 标记的方法和 @PreDestroy标记的方法 可以有多个的
				for (LifecycleElement element : destroyMethodsToUse) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking destroy method on bean '" + beanName + "': " + element.getMethod());
					}
					// TODO: 去执行销毁方法
					element.invoke(target);
				}
			}
		}

		/**
		 * TODO: 这个方法其实就是判断下这个class里面有没有destroyMethods
		 *
		 * @return
		 */
		public boolean hasDestroyMethods() {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			return !destroyMethodsToUse.isEmpty();
		}
	}


	/**
	 * TODO: 其实这个就是维护了一个method引用
	 *
	 * Class representing injection information about an annotated method.
	 */
	private static class LifecycleElement {

		/**
		 * TODO: 内部持有的method,可能是InitMethod，也可能是destroyMethod
		 */
		private final Method method;

		private final String identifier;

		public LifecycleElement(Method method) {
			if (method.getParameterCount() != 0) {
				throw new IllegalStateException("Lifecycle method annotation requires a no-arg method: " + method);
			}
			this.method = method;
			this.identifier = (Modifier.isPrivate(method.getModifiers()) ?
					ClassUtils.getQualifiedMethodName(method) : method.getName());
		}

		public Method getMethod() {
			return this.method;
		}

		public String getIdentifier() {
			return this.identifier;
		}

		/**
		 * TODO: 去执行这个方法, 先makeAccessible，然后直接去执行
		 *
		 * @param target
		 * @throws Throwable
		 */
		public void invoke(Object target) throws Throwable {
			ReflectionUtils.makeAccessible(this.method);
			this.method.invoke(target, (Object[]) null);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LifecycleElement)) {
				return false;
			}
			LifecycleElement otherElement = (LifecycleElement) other;
			return (this.identifier.equals(otherElement.identifier));
		}

		@Override
		public int hashCode() {
			return this.identifier.hashCode();
		}
	}

}
