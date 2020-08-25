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

package org.springframework.aop.scope;

import java.lang.reflect.Modifier;

import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.aop.target.SimpleBeanTargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * TODO: 主要作用，用来产生代理，什么代理呢？比如bean的scope是多例的，然后单例bean注入多例bean的时候，如果开启代理的话，实际上注入的是这个FactoryBean，然后
 * TODO: 通过它来实现动态的拿指定作用域中的bean
 * Convenient proxy factory bean for scoped objects.
 *
 * <p>Proxies created using this factory bean are thread-safe singletons
 * and may be injected into shared objects, with transparent scoping behavior.
 *
 * <p>Proxies returned by this class implement the {@link ScopedObject} interface.
 * This presently allows for removing the corresponding object from the scope,
 * seamlessly creating a new instance in the scope on next access.
 *
 * <p>Please note that the proxies created by this factory are
 * <i>class-based</i> proxies by default. This can be customized
 * through switching the "proxyTargetClass" property to "false".
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0
 * @see #setProxyTargetClass
 */
@SuppressWarnings("serial")
public class ScopedProxyFactoryBean extends ProxyConfig
		implements FactoryBean<Object>, BeanFactoryAware, AopInfrastructureBean {

	/** The TargetSource that manages scoping. */
	/**
	 * TODO: 这个targetSource很重要，通过它来得到实际的bean对象
	 */
	private final SimpleBeanTargetSource scopedTargetSource = new SimpleBeanTargetSource();

	/** The name of the target bean. */
	@Nullable
	private String targetBeanName;

	/** The cached singleton proxy. */
	@Nullable
	private Object proxy;


	/**
	 * Create a new ScopedProxyFactoryBean instance.
	 */
	public ScopedProxyFactoryBean() {
		setProxyTargetClass(true);
	}


	/**
	 * Set the name of the bean that is to be scoped.
	 */
	public void setTargetBeanName(String targetBeanName) {
		this.targetBeanName = targetBeanName;
		// TODO: 把targetBeanName设置进scopedTargetSource里面去，到时候可以动态的根据beanName来得到相应的bean
		this.scopedTargetSource.setTargetBeanName(targetBeanName);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableBeanFactory)) {
			throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
		}
		ConfigurableBeanFactory cbf = (ConfigurableBeanFactory) beanFactory;
		// TODO: 将beanFactory设置进去
		this.scopedTargetSource.setBeanFactory(beanFactory);
		// TODO: 创建代理工厂
		ProxyFactory pf = new ProxyFactory();
		pf.copyFrom(this);
		// TODO: 设置targetSource
		pf.setTargetSource(this.scopedTargetSource);

		Assert.notNull(this.targetBeanName, "Property 'targetBeanName' is required");
		// TODO: 把这个targetBeanName的bean的类型拿到
		Class<?> beanType = beanFactory.getType(this.targetBeanName);
		// TODO: 如果beanType为null，报错
		if (beanType == null) {
			throw new IllegalStateException("Cannot create scoped proxy for bean '" + this.targetBeanName +
					"': Target type could not be determined at the time of proxy creation.");
		}
		// TODO: 设置jdk代理，如果是个接口，如果isProxyTargetClass 为false , 或者是私有类型的 都设置为jdk的代理
		if (!isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {
			pf.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, cbf.getBeanClassLoader()));
		}

		// Add an introduction that implements only the methods on ScopedObject.
		// TODO: 这里用到了引介增强啊，给相应的bean增加接口中的方法，这个很强大，这个主要是委托的接口实现类，用它来实现接口中的方法
		ScopedObject scopedObject = new DefaultScopedObject(cbf, this.scopedTargetSource.getTargetBeanName());
		// TODO: 添加 方法通知
		pf.addAdvice(new DelegatingIntroductionInterceptor(scopedObject));

		// Add the AopInfrastructureBean marker to indicate that the scoped proxy
		// itself is not subject to auto-proxying! Only its target bean is.
		// TODO: 标记aop代理类
		pf.addInterface(AopInfrastructureBean.class);

		this.proxy = pf.getProxy(cbf.getBeanClassLoader());
	}


	@Override
	public Object getObject() {
		if (this.proxy == null) {
			throw new FactoryBeanNotInitializedException();
		}
		return this.proxy;
	}

	@Override
	public Class<?> getObjectType() {
		if (this.proxy != null) {
			return this.proxy.getClass();
		}
		return this.scopedTargetSource.getTargetClass();
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
