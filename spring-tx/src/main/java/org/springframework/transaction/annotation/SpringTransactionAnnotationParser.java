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

package org.springframework.transaction.annotation;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Strategy implementation for parsing Spring's {@link Transactional} annotation.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
@SuppressWarnings("serial")
public class SpringTransactionAnnotationParser implements TransactionAnnotationParser, Serializable {

	/**
	 * TODO: 此方法对外暴露，表示获取该方法/类上面的TransactionAttribute
	 * @param element the annotated method or class
	 * @return
	 */
	@Override
	@Nullable
	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement element) {
		AnnotationAttributes attributes = AnnotatedElementUtils.findMergedAnnotationAttributes(
				element, Transactional.class, false, false);
		if (attributes != null) {
			// TODO: 把事务注解 交给它，然后最终转换成事务的属性类
			return parseTransactionAnnotation(attributes);
		}
		// TODO: 没有注解，那就返回null喽
		else {
			return null;
		}
	}

	public TransactionAttribute parseTransactionAnnotation(Transactional ann) {
		return parseTransactionAnnotation(AnnotationUtils.getAnnotationAttributes(ann, false, false));
	}

	/**
	 * TODO: 把事务注解转为事务属性
	 * @param attributes
	 * @return
	 */
	protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
		// TODO: 注意此处用的 RuleBasedTransactionAttribute 因为它可以指定不需要回滚的类
		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();
		// TODO: 事务的传播行为枚举，内部定义了7种事务传播行为
		Propagation propagation = attributes.getEnum("propagation");
		rbta.setPropagationBehavior(propagation.value());
		// TODO: 事务的隔离级别，一共是4个，枚举里提供了一个默认值，也就是上面的TransactionDefinition.ISOLATION_DEFAULT
		Isolation isolation = attributes.getEnum("isolation");
		rbta.setIsolationLevel(isolation.value());
		// TODO: 设置事务的超时时间
		rbta.setTimeout(attributes.getNumber("timeout").intValue());
		// TODO: 是否是只读事务
		rbta.setReadOnly(attributes.getBoolean("readOnly"));
		// TODO: 这个属性，是指定事务管理器的beanName的，若不指定，那就按照类型去找了，若容器中存在多个事务管理器，但又没指定名字，那就报错了
		rbta.setQualifier(attributes.getString("value"));

		List<RollbackRuleAttribute> rollbackRules = new ArrayList<>();
		// TODO: rollbackFor可以指定需要回滚的异常，可以指定多个，若不指定，默认为RuntimeException或者Error
		for (Class<?> rbRule : attributes.getClassArray("rollbackFor")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		// TODO: 全类名的方式
		for (String rbRule : attributes.getStringArray("rollbackForClassName")) {
			rollbackRules.add(new RollbackRuleAttribute(rbRule));
		}
		// TODO: 指定不需要回滚的异常类型们
		for (Class<?> rbRule : attributes.getClassArray("noRollbackFor")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		for (String rbRule : attributes.getStringArray("noRollbackForClassName")) {
			rollbackRules.add(new NoRollbackRuleAttribute(rbRule));
		}
		// TODO: 然后set进去
		rbta.setRollbackRules(rollbackRules);

		return rbta;
	}


	@Override
	public boolean equals(Object other) {
		return (this == other || other instanceof SpringTransactionAnnotationParser);
	}

	@Override
	public int hashCode() {
		return SpringTransactionAnnotationParser.class.hashCode();
	}

}
