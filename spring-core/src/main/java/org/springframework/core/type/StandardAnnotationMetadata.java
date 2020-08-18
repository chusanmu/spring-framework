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

package org.springframework.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

/**
 * {@link AnnotationMetadata} implementation that uses standard reflection
 * to introspect a given {@link Class}.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.5
 */
public class StandardAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {

	private final Annotation[] annotations;

	private final boolean nestedAnnotationsAsMap;


	/**
	 * Create a new {@code StandardAnnotationMetadata} wrapper for the given Class.
	 * @param introspectedClass the Class to introspect
	 * @see #StandardAnnotationMetadata(Class, boolean)
	 */
	public StandardAnnotationMetadata(Class<?> introspectedClass) {
		this(introspectedClass, false);
	}

	/**
	 * Create a new {@link StandardAnnotationMetadata} wrapper for the given Class,
	 * providing the option to return any nested annotations or annotation arrays in the
	 * form of {@link org.springframework.core.annotation.AnnotationAttributes} instead
	 * of actual {@link Annotation} instances.
	 * @param introspectedClass the Class to introspect
	 * @param nestedAnnotationsAsMap return nested annotations and annotation arrays as
	 * {@link org.springframework.core.annotation.AnnotationAttributes} for compatibility
	 * with ASM-based {@link AnnotationMetadata} implementations
	 * @since 3.1.1
	 */
	public StandardAnnotationMetadata(Class<?> introspectedClass, boolean nestedAnnotationsAsMap) {
		super(introspectedClass);
		// TODO: 拿到类上面的所有的注解
		this.annotations = introspectedClass.getAnnotations();
		this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
	}


	/**
	 * TODO: 把类上所有标注的注解的 全限定名拿到
	 * @return
	 */
	@Override
	public Set<String> getAnnotationTypes() {
		Set<String> types = new LinkedHashSet<>();
		for (Annotation ann : this.annotations) {
			types.add(ann.annotationType().getName());
		}
		return types;
	}

	@Override
	public Set<String> getMetaAnnotationTypes(String annotationName) {
		return (this.annotations.length > 0 ?
				// TODO: 这里通过工具类去拿的，拿到注解里面的某一个元注解
				AnnotatedElementUtils.getMetaAnnotationTypes(getIntrospectedClass(), annotationName) :
				Collections.emptySet());
	}

	/**
	 * TODO: 判断某个注解 在类上是否存在
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return
	 */
	@Override
	public boolean hasAnnotation(String annotationName) {
		for (Annotation ann : this.annotations) {
			// TODO: 如果存在那就返回true喽
			if (ann.annotationType().getName().equals(annotationName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * TODO: 判断注解中是否拥有某一个元注解
	 * @param annotationName
	 * @return
	 */
	@Override
	public boolean hasMetaAnnotation(String annotationName) {
		return (this.annotations.length > 0 &&
				AnnotatedElementUtils.hasMetaAnnotationTypes(getIntrospectedClass(), annotationName));
	}

	/**
	 * todo: 也是判断某个注解是否存在
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return
	 */
	@Override
	public boolean isAnnotated(String annotationName) {
		return (this.annotations.length > 0 &&
				AnnotatedElementUtils.isAnnotated(getIntrospectedClass(), annotationName));
	}

	/**
	 * TODO: 拿到某一个注解里面的所有的属性值 作为一个map返回
	 * @param annotationName the fully qualified class name of the annotation
	 * type to look for
	 * @return
	 */
	@Override
	public Map<String, Object> getAnnotationAttributes(String annotationName) {
		return getAnnotationAttributes(annotationName, false);
	}

	@Override
	@Nullable
	public Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		// TODO: 也是利用工具类去做的
		return (this.annotations.length > 0 ? AnnotatedElementUtils.getMergedAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, this.nestedAnnotationsAsMap) : null);
	}

	@Override
	@Nullable
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName) {
		return getAllAnnotationAttributes(annotationName, false);
	}

	@Override
	@Nullable
	public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
		return (this.annotations.length > 0 ? AnnotatedElementUtils.getAllAnnotationAttributes(
				getIntrospectedClass(), annotationName, classValuesAsString, this.nestedAnnotationsAsMap) : null);
	}

	/**
	 * TODO: 判断是否拥有某一个注解标注的方法
	 * @param annotationName the fully qualified class name of the annotation
	 * @return
	 */
	@Override
	public boolean hasAnnotatedMethods(String annotationName) {
		try {
			// TODO: 把它所有的方法拿到
			Method[] methods = getIntrospectedClass().getDeclaredMethods();
			for (Method method : methods) {
				// TODO: 非桥接方法，并且方法上有这个注解，则返回true
				if (!method.isBridge() && method.getAnnotations().length > 0 &&
						AnnotatedElementUtils.isAnnotated(method, annotationName)) {
					return true;
				}
			}
			return false;
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
		}
	}

	@Override
	public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
		try {
			// TODO: 把被annotationName标注的方法返回
			Method[] methods = getIntrospectedClass().getDeclaredMethods();
			Set<MethodMetadata> annotatedMethods = new LinkedHashSet<>(4);
			for (Method method : methods) {
				if (!method.isBridge() && method.getAnnotations().length > 0 &&
						AnnotatedElementUtils.isAnnotated(method, annotationName)) {
					// TODO: 加到集合里面去
					annotatedMethods.add(new StandardMethodMetadata(method, this.nestedAnnotationsAsMap));
				}
			}
			return annotatedMethods;
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
		}
	}

}
