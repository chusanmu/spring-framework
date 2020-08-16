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

package org.springframework.transaction.interceptor;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;
import org.springframework.lang.Nullable;

/**
 * Advisor driven by a {@link TransactionAttributeSource}, used to include
 * a transaction advice bean for methods that are transactional.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 * @see #setAdviceBeanName
 * @see TransactionInterceptor
 * @see TransactionAttributeSourceAdvisor
 */
@SuppressWarnings("serial")
public class BeanFactoryTransactionAttributeSourceAdvisor extends AbstractBeanFactoryPointcutAdvisor {

	@Nullable
	private TransactionAttributeSource transactionAttributeSource;

	/**
	 * TODO: 这个就是切面啊，它决定了哪些类会被切入，从而生成代理对象
	 */
	private final TransactionAttributeSourcePointcut pointcut = new TransactionAttributeSourcePointcut() {
		@Override
		@Nullable
		protected TransactionAttributeSource getTransactionAttributeSource() {
			return transactionAttributeSource;
		}
	};


	/**
	 * TODO: 手动设置一个事务属性源
	 * Set the transaction attribute source which is used to find transaction
	 * attributes. This should usually be identical to the source reference
	 * set on the transaction interceptor itself.
	 * @see TransactionInterceptor#setTransactionAttributeSource
	 */
	public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
		this.transactionAttributeSource = transactionAttributeSource;
	}

	/**
	 * TODO: 可以指定classFilter, 默认情况下是ClassFilter.TRUE, 匹配所有的类
	 * Set the {@link ClassFilter} to use for this pointcut.
	 * Default is {@link ClassFilter#TRUE}.
	 */
	public void setClassFilter(ClassFilter classFilter) {
		this.pointcut.setClassFilter(classFilter);
	}

	/**
	 * TODO： 这地方就是要自己使用的pointcut去切入
	 * @return
	 */
	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

}
