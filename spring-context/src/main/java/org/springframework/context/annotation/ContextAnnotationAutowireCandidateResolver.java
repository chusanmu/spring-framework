/*
 * Copyright 2002-2017 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 它还支持lazy，延迟处理
 * Complete implementation of the
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver} strategy
 * interface, providing support for qualifier annotations as well as for lazy resolution
 * driven by the {@link Lazy} annotation in the {@code context.annotation} package.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
public class ContextAnnotationAutowireCandidateResolver extends QualifierAnnotationAutowireCandidateResolver {

	/**
	 * TODO: 返回该lazy proxy表示延迟初始化，实现过程是查看在@Autowired 注解处是否使用了@Lazy=true注解
	 * @param descriptor
	 * @param beanName
	 * @return
	 */
	@Override
	@Nullable
	public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
		// TODO: 如果isLazy=true, 那就返回一个代理，否则返回null, 相当于若标注了@Lazy注解，就会返回一个代理，当然@Lazy注解的value值不能是false
		return (isLazy(descriptor) ? buildLazyResolutionProxy(descriptor, beanName) : null);
	}

	/**
	 * TODO: 这个比较简单，@Lazy注解标注了就行,value属性默认值是true，@Lazy支持标注在属性上和方法入参上
	 * @param descriptor
	 * @return
	 */
	protected boolean isLazy(DependencyDescriptor descriptor) {
		for (Annotation ann : descriptor.getAnnotations()) {
			Lazy lazy = AnnotationUtils.getAnnotation(ann, Lazy.class);
			if (lazy != null && lazy.value()) {
				return true;
			}
		}
		MethodParameter methodParam = descriptor.getMethodParameter();
		if (methodParam != null) {
			Method method = methodParam.getMethod();
			if (method == null || void.class == method.getReturnType()) {
				Lazy lazy = AnnotationUtils.getAnnotation(methodParam.getAnnotatedElement(), Lazy.class);
				if (lazy != null && lazy.value()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 核心内容，本类的灵魂，标注有@Lazy注解完成注入的时候，最终注入的只是一个临时生成的代理对象，只有在真正执行目标方法的时候，才会去容器内拿到真正的bean实例来执行目标方法
	 * TODO: 先随便给你整个代理对象，等你真正用的时候，才去容器里面去拿
	 * @param descriptor
	 * @param beanName
	 * @return
	 */
	protected Object buildLazyResolutionProxy(final DependencyDescriptor descriptor, final @Nullable String beanName) {
		Assert.state(getBeanFactory() instanceof DefaultListableBeanFactory,
				"BeanFactory needs to be a DefaultListableBeanFactory");
		// TODO: 面向实现类编程，使用了DefaultListableBeanFactory.doResolverDependency()方法
		final DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) getBeanFactory();
		// TODO: TargetSource是它实现懒加载的核心原因
		TargetSource ts = new TargetSource() {
			@Override
			public Class<?> getTargetClass() {
				return descriptor.getDependencyType();
			}
			@Override
			public boolean isStatic() {
				return false;
			}

			/**
			 * TODO: getTarget是调用代理方法的时候会调用的，所以执行每个代理方法都会执行此方法，这也是为何doResolveDependence
			 * @return
			 */
			@Override
			public Object getTarget() {
				// TODO: 到这里 才真正的去解析依赖
				Object target = beanFactory.doResolveDependency(descriptor, beanName, null, null);
				if (target == null) {
					Class<?> type = getTargetClass();
					// TODO: 对多值注入的空值的友好处理
					if (Map.class == type) {
						return Collections.emptyMap();
					}
					else if (List.class == type) {
						return Collections.emptyList();
					}
					else if (Set.class == type || Collection.class == type) {
						return Collections.emptySet();
					}
					throw new NoSuchBeanDefinitionException(descriptor.getResolvableType(),
							"Optional dependency not present for lazy injection point");
				}
				return target;
			}
			@Override
			public void releaseTarget(Object target) {
			}
		};
		// TODO: 使用ProxyFactory 给 ts 生成一个代理
		// TODO: 由此可见，最终生成的代理对象的目标对象其实是targetSource, 而targetSource的目标才是我们业务的对象
		ProxyFactory pf = new ProxyFactory();
		pf.setTargetSource(ts);
		Class<?> dependencyType = descriptor.getDependencyType();
		// TODO: 如果注入的语句是个接口，那么值是true,把这个接口类型也放进去
		if (dependencyType.isInterface()) {
			pf.addInterface(dependencyType);
		}
		return pf.getProxy(beanFactory.getBeanClassLoader());
	}

}
