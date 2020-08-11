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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.GenericTypeAwareAutowireCandidateResolver;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * TODO: 它不进京能够处理 @Value @Qualifier 等还能处理泛型依赖注入，因此功能已经很完善了，它不仅仅能够处理@Qualifier注解，也能够处理通过@Value注解解析表达式得到的 suggested  value，也就是说它还实现了接口方法 getSuggestedValue()
 * {@link AutowireCandidateResolver} implementation that matches bean definition qualifiers
 * against {@link Qualifier qualifier annotations} on the field or parameter to be autowired.
 * Also supports suggested expression values through a {@link Value value} annotation.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.5
 * @see AutowireCandidateQualifier
 * @see Qualifier
 * @see Value
 */
public class QualifierAnnotationAutowireCandidateResolver extends GenericTypeAwareAutowireCandidateResolver {

	/**
	 * TODO: 支持的注解的类型，默认支持@Qualifier和JSR-330的javax.inject.Qualifier注解
	 */
	private final Set<Class<? extends Annotation>> qualifierTypes = new LinkedHashSet<>(2);

	private Class<? extends Annotation> valueAnnotationType = Value.class;


	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for Spring's standard {@link Qualifier} annotation.
	 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
	 */
	@SuppressWarnings("unchecked")
	public QualifierAnnotationAutowireCandidateResolver() {
		this.qualifierTypes.add(Qualifier.class);
		try {
			this.qualifierTypes.add((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Qualifier",
							QualifierAnnotationAutowireCandidateResolver.class.getClassLoader()));
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}

	/**
	 * 	TODO: 可以通过构造函数，增加自定义的注解的支持
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation type.
	 * @param qualifierType the qualifier annotation to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Class<? extends Annotation> qualifierType) {
		Assert.notNull(qualifierType, "'qualifierType' must not be null");
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation types.
	 * @param qualifierTypes the qualifier annotations to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Set<Class<? extends Annotation>> qualifierTypes) {
		Assert.notNull(qualifierTypes, "'qualifierTypes' must not be null");
		this.qualifierTypes.addAll(qualifierTypes);
	}


	/**
	 * Register the given type to be used as a qualifier when autowiring.
	 * <p>This identifies qualifier annotations for direct use (on fields,
	 * method parameters and constructor parameters) as well as meta
	 * annotations that in turn identify actual qualifier annotations.
	 * <p>This implementation only supports annotations as qualifier types.
	 * The default is Spring's {@link Qualifier} annotation which serves
	 * as a qualifier for direct use and also as a meta annotation.
	 * @param qualifierType the annotation type to register
	 */
	public void addQualifierType(Class<? extends Annotation> qualifierType) {
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * TODO: value注解类型 spring也是允许我们改成自己的类型的
	 * Set the 'value' annotation type, to be used on fields, method parameters
	 * and constructor parameters.
	 * <p>The default value annotation type is the Spring-provided
	 * {@link Value} annotation.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate a default value
	 * expression for a specific argument.
	 */
	public void setValueAnnotationType(Class<? extends Annotation> valueAnnotationType) {
		this.valueAnnotationType = valueAnnotationType;
	}


	/**
	 * Determine whether the provided bean definition is an autowire candidate.
	 * <p>To be considered a candidate the bean's <em>autowire-candidate</em>
	 * attribute must not have been set to 'false'. Also, if an annotation on
	 * the field or parameter to be autowired is recognized by this bean factory
	 * as a <em>qualifier</em>, the bean must 'match' against the annotation as
	 * well as any attributes it may contain. The bean definition must contain
	 * the same qualifier or match by meta attributes. A "value" attribute will
	 * fallback to match against the bean name or an alias if a qualifier or
	 * attribute does not match.
	 * @see Qualifier
	 */
	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		boolean match = super.isAutowireCandidate(bdHolder, descriptor);
		// TODO: 到了这，如果是false,说明泛型没有匹配上，那就不用继续往下走了
		// TODO: 如果为true,那就继续，解析@Qualifier注解了，
		if (match) {
			// TODO: 看看有没有标注@Qualifier注解，没有标注也是返回true，@Qualifier注解在此处生效，最终可能匹配出一个或者0个出来
			match = checkQualifiers(bdHolder, descriptor.getAnnotations());
			if (match) {
				// TODO: 兼容到方法级别的注入，这里处理的是方法入参们，只有方法有入参才需要继续解析
				MethodParameter methodParam = descriptor.getMethodParameter();
				if (methodParam != null) {
					// TODO: 表示这个入参所属于的方法，如果它不属于任何方法或者属于方法的返回值void，才去看它头上标注的@Qualifier注解
					Method method = methodParam.getMethod();
					if (method == null || void.class == method.getReturnType()) {
						match = checkQualifiers(bdHolder, methodParam.getMethodAnnotations());
					}
				}
			}
		}
		return match;
	}

	/**
	 * TODO: 将给定的Qualifier注解与候选bean定义匹配
	 * Match the given qualifier annotations against the candidate bean definition.
	 */
	protected boolean checkQualifiers(BeanDefinitionHolder bdHolder, Annotation[] annotationsToSearch) {
		// TODO: 这里一般会有两个注解，一个@Autowired 一个@Qualifier，或者还有其余的组合注解
		if (ObjectUtils.isEmpty(annotationsToSearch)) {
			return true;
		}
		SimpleTypeConverter typeConverter = new SimpleTypeConverter();
		for (Annotation annotation : annotationsToSearch) {
			Class<? extends Annotation> type = annotation.annotationType();
			boolean checkMeta = true;
			boolean fallbackToMeta = false;
			// TODO: 最重要的方法就是它了
			if (isQualifier(type)) {
				if (!checkQualifier(bdHolder, annotation, typeConverter)) {
					fallbackToMeta = true;
				}
				else {
					checkMeta = false;
				}
			}
			if (checkMeta) {
				boolean foundMeta = false;
				for (Annotation metaAnn : type.getAnnotations()) {
					Class<? extends Annotation> metaType = metaAnn.annotationType();
					if (isQualifier(metaType)) {
						foundMeta = true;
						// Only accept fallback match if @Qualifier annotation has a value...
						// Otherwise it is just a marker for a custom qualifier annotation.
						if ((fallbackToMeta && StringUtils.isEmpty(AnnotationUtils.getValue(metaAnn))) ||
								!checkQualifier(bdHolder, metaAnn, typeConverter)) {
							return false;
						}
					}
				}
				if (fallbackToMeta && !foundMeta) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Checks whether the given annotation type is a recognized qualifier type.
	 */
	protected boolean isQualifier(Class<? extends Annotation> annotationType) {
		for (Class<? extends Annotation> qualifierType : this.qualifierTypes) {
			if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Match the given qualifier annotation against the candidate bean definition.
	 */
	protected boolean checkQualifier(
			BeanDefinitionHolder bdHolder, Annotation annotation, TypeConverter typeConverter) {

		Class<? extends Annotation> type = annotation.annotationType();
		RootBeanDefinition bd = (RootBeanDefinition) bdHolder.getBeanDefinition();

		AutowireCandidateQualifier qualifier = bd.getQualifier(type.getName());
		if (qualifier == null) {
			qualifier = bd.getQualifier(ClassUtils.getShortName(type));
		}
		if (qualifier == null) {
			// First, check annotation on qualified element, if any
			Annotation targetAnnotation = getQualifiedElementAnnotation(bd, type);
			// Then, check annotation on factory method, if applicable
			if (targetAnnotation == null) {
				targetAnnotation = getFactoryMethodAnnotation(bd, type);
			}
			if (targetAnnotation == null) {
				RootBeanDefinition dbd = getResolvedDecoratedDefinition(bd);
				if (dbd != null) {
					targetAnnotation = getFactoryMethodAnnotation(dbd, type);
				}
			}
			if (targetAnnotation == null) {
				// Look for matching annotation on the target class
				if (getBeanFactory() != null) {
					try {
						Class<?> beanType = getBeanFactory().getType(bdHolder.getBeanName());
						if (beanType != null) {
							targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(beanType), type);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Not the usual case - simply forget about the type check...
					}
				}
				if (targetAnnotation == null && bd.hasBeanClass()) {
					targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type);
				}
			}
			if (targetAnnotation != null && targetAnnotation.equals(annotation)) {
				return true;
			}
		}

		Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
		if (attributes.isEmpty() && qualifier == null) {
			// If no attributes, the qualifier must be present
			return false;
		}
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attributeName = entry.getKey();
			Object expectedValue = entry.getValue();
			Object actualValue = null;
			// Check qualifier first
			if (qualifier != null) {
				actualValue = qualifier.getAttribute(attributeName);
			}
			if (actualValue == null) {
				// Fall back on bean definition attribute
				actualValue = bd.getAttribute(attributeName);
			}
			if (actualValue == null && attributeName.equals(AutowireCandidateQualifier.VALUE_KEY) &&
					expectedValue instanceof String && bdHolder.matchesName((String) expectedValue)) {
				// Fall back on bean name (or alias) match
				continue;
			}
			if (actualValue == null && qualifier != null) {
				// Fall back on default, but only if the qualifier is present
				actualValue = AnnotationUtils.getDefaultValue(annotation, attributeName);
			}
			if (actualValue != null) {
				actualValue = typeConverter.convertIfNecessary(actualValue, expectedValue.getClass());
			}
			if (!expectedValue.equals(actualValue)) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	protected Annotation getQualifiedElementAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		AnnotatedElement qualifiedElement = bd.getQualifiedElement();
		return (qualifiedElement != null ? AnnotationUtils.getAnnotation(qualifiedElement, type) : null);
	}

	@Nullable
	protected Annotation getFactoryMethodAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		Method resolvedFactoryMethod = bd.getResolvedFactoryMethod();
		return (resolvedFactoryMethod != null ? AnnotationUtils.getAnnotation(resolvedFactoryMethod, type) : null);
	}


	/**
	 * Determine whether the given dependency declares an autowired annotation,
	 * checking its required flag.
	 * @see Autowired#required()
	 */
	@Override
	public boolean isRequired(DependencyDescriptor descriptor) {
		if (!super.isRequired(descriptor)) {
			return false;
		}
		Autowired autowired = descriptor.getAnnotation(Autowired.class);
		return (autowired == null || autowired.required());
	}

	/**
	 * TODO: 看标注的所有注解，是否有@Qualifier这个注解
	 * Determine whether the given dependency declares a qualifier annotation.
	 * @see #isQualifier(Class)
	 * @see Qualifier
	 */
	@Override
	public boolean hasQualifier(DependencyDescriptor descriptor) {
		for (Annotation ann : descriptor.getAnnotations()) {
			if (isQualifier(ann.annotationType())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * TODO: 解析@Value注解，需要注意此类它不负责解析占位符啥的，只负责把字符串返回
	 * TODO: 最终是交给value = evaluateBeanDefinitionString(strValue,bd) 它处理
	 * Determine whether the given dependency declares a value annotation.
	 * @see Value
	 */
	@Override
	@Nullable
	public Object getSuggestedValue(DependencyDescriptor descriptor) {
		// TODO: 拿到value注解，当然不一定是@Value注解，可以自定义的，并且拿到他的注解属性value值
		Object value = findValue(descriptor.getAnnotations());
		if (value == null) {
			// TODO: 相当于@Value注解标注在方法入参上，也是可以的
			MethodParameter methodParam = descriptor.getMethodParameter();
			if (methodParam != null) {
				value = findValue(methodParam.getMethodAnnotations());
			}
		}
		return value;
	}

	/**
	 * Determine a suggested value from any of the given candidate annotations.
	 */
	@Nullable
	protected Object findValue(Annotation[] annotationsToSearch) {
		if (annotationsToSearch.length > 0) {   // qualifier annotations have to be local
			AnnotationAttributes attr = AnnotatedElementUtils.getMergedAnnotationAttributes(
					AnnotatedElementUtils.forAnnotations(annotationsToSearch), this.valueAnnotationType);
			if (attr != null) {
				return extractValue(attr);
			}
		}
		return null;
	}

	/**
	 * Extract the value attribute from the given annotation.
	 * @since 4.3
	 */
	protected Object extractValue(AnnotationAttributes attr) {
		Object value = attr.get(AnnotationUtils.VALUE);
		if (value == null) {
			throw new IllegalStateException("Value annotation must have a value attribute");
		}
		return value;
	}

}
