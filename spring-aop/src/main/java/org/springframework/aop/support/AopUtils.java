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

package org.springframework.aop.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility methods for AOP support code.
 *
 * <p>Mainly for internal use within Spring's AOP support.
 *
 * <p>See {@link org.springframework.aop.framework.AopProxyUtils} for a
 * collection of framework-specific AOP utility methods which depend
 * on internals of Spring's AOP framework implementation.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public abstract class AopUtils {

	/**
	 * TODO: 判断该对象是否经过Aop代理产生的
	 * Check whether the given object is a JDK dynamic proxy or a CGLIB proxy.
	 * <p>This method additionally checks if the given object is an instance
	 * of {@link SpringProxy}.
	 * @param object the object to check
	 * @see #isJdkDynamicProxy
	 * @see #isCglibProxy
	 */
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				(Proxy.isProxyClass(object.getClass()) || ClassUtils.isCglibProxyClass(object.getClass())));
	}

	/**
	 * TODO: 判断该对象是否是经过JDK动态代理产生的
	 * Check whether the given object is a JDK dynamic proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link Proxy#isProxyClass(Class)} by additionally checking if the
	 * given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see java.lang.reflect.Proxy#isProxyClass
	 */
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * TODO: 判断该对象是否是cglib产生的
	 * Check whether the given object is a CGLIB proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link ClassUtils#isCglibProxy(Object)} by additionally checking if
	 * the given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see ClassUtils#isCglibProxy(Object)
	 */
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && ClassUtils.isCglibProxy(object));
	}

	/**
	 * TODO: 拿到被代理的目标class对象
	 * Determine the target class of the given bean instance which might be an AOP proxy.
	 * <p>Returns the target class for an AOP proxy or the plain class otherwise.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the target class (or the plain class of the given object as fallback;
	 * never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass(Object)
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		// TODO: 看是否实现了TargetClassAware接口，如果是，则转为TargetClassAware
		if (candidate instanceof TargetClassAware) {
			result = ((TargetClassAware) candidate).getTargetClass();
		}
		if (result == null) {
			// TODO: 如果是cglib代理，把它的父类拿到
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 * @param method the method to check
	 * @param targetType the target type to search methods on (typically an AOP proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 * target type (typically due to a proxy mismatch)
	 * @since 4.3
	 * @see MethodIntrospector#selectInvocableMethod(Method, Class)
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
					"be delegated to target bean. Switch its visibility to package or protected.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
		return methodToUse;
	}

	/**
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * Determine whether the given method is a "finalize" method.
	 * @see java.lang.Object#finalize()
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * Given a method, which may come from an interface, and a target class used
	 * in the current AOP invocation, find the corresponding target method if there
	 * is one. E.g. the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {@link org.springframework.util.ClassUtils#getMostSpecificMethod},
	 * this method resolves Java 5 bridge methods in order to retrieve attributes
	 * from the <i>original</i> method definition.
	 * @param method the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation.
	 * May be {@code null} or may not even implement the method.
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} doesn't implement it or is {@code null}
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		// TODO: 根据传进来的class,和method, 拿到最终执行的那个方法
		Method resolvedMethod = ClassUtils.getMostSpecificMethod(method, specificTargetClass);
		// If we are dealing with method with generic parameters, find the original method.
		// TODO: 如果有泛型，则去拿到原始的那个方法，不要桥接方法，把原始方法拿到
		return BridgeMethodResolver.findBridgedMethod(resolvedMethod);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @param hasIntroductions whether or not the advisor chain
	 * for this bean includes any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");
		// TODO: 如果目标类 不匹配classFilter, 那就没什么说的了，直接返回false.
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}
		// TODO: 从pointcut中拿出MethodMatcher
		MethodMatcher methodMatcher = pc.getMethodMatcher();
		// TODO: 如果方法匹配器是true,就是全部匹配，那就返回true,就是代理目标类是可以匹配的
		if (methodMatcher == MethodMatcher.TRUE) {
			// No need to iterate the methods if we're matching any method anyway...
			return true;
		}

		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher) {
			introductionAwareMethodMatcher = (IntroductionAwareMethodMatcher) methodMatcher;
		}
		// TODO: 存目标类，如果目标class是一个代理 是经过代理的
		Set<Class<?>> classes = new LinkedHashSet<>();
		if (!Proxy.isProxyClass(targetClass)) {
			// TODO: 把代理类拿出来
			classes.add(ClassUtils.getUserClass(targetClass));
		}
		// TODO: 再去把它的接口拿出来，所有的接口拿出来
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));
		// TODO: 遍历所有的class, 包括它所实现的一些接口，然后再把里面所有，也就是它本类的所有方法拿到
		for (Class<?> clazz : classes) {
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
				// TODO: 逐个方法去进行匹配
				if (introductionAwareMethodMatcher != null ?
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * This is an important test as it can be used to optimize
	 * out a advisor for a class.
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		return canApply(advisor, targetClass, false);
	}

	/**
	 * TODO: 判断给定的advisor能否应用到目标class上面
	 * Can the given advisor apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize out a advisor for a class.
	 * This version also takes into account introductions (for IntroductionAwareMethodMatchers).
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @param hasIntroductions whether or not the advisor chain for this bean includes
	 * any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		if (advisor instanceof IntroductionAdvisor) {
			return ((IntroductionAdvisor) advisor).getClassFilter().matches(targetClass);
		}
		else if (advisor instanceof PointcutAdvisor) {
			// TODO: 转为pointcutAdvisor 然后利用pointcut去判断
			PointcutAdvisor pca = (PointcutAdvisor) advisor;
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		}
		else {
			// It doesn't have a pointcut so we assume it applies.
			return true;
		}
	}

	/**
	 * TODO: 通过一堆advisors 找出可以应用到 clazz上面的
	 * Determine the sublist of the {@code candidateAdvisors} list
	 * that is applicable to the given class.
	 * @param candidateAdvisors the Advisors to evaluate
	 * @param clazz the target class
	 * @return sublist of Advisors that can apply to an object of the given class
	 * (may be the incoming List as-is)
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		// TODO: 如果为空，那就直接返回空吧
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		// TODO: 创建一个List,用来存放合适的advisors
		List<Advisor> eligibleAdvisors = new ArrayList<>();
		for (Advisor candidate : candidateAdvisors) {
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();
		for (Advisor candidate : candidateAdvisors) {
			// TODO: 已经处理过了，就不再去处理了
			if (candidate instanceof IntroductionAdvisor) {
				// already processed
				continue;
			}
			// TODO: 然后再继续匹配
			if (canApply(candidate, clazz, hasIntroductions)) {
				eligibleAdvisors.add(candidate);
			}
		}
		return eligibleAdvisors;
	}

	/**
	 * TODO：直接用反射的方式，直接调用目标对象的目标方法
	 * Invoke the given target via reflection, as part of an AOP method invocation.
	 * @param target the target object
	 * @param method the method to invoke
	 * @param args the arguments for the method
	 * @return the invocation result, if any
	 * @throws Throwable if thrown by the target method
	 * @throws org.springframework.aop.AopInvocationException in case of a reflection error
	 */
	@Nullable
	public static Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, Object[] args)
			throws Throwable {

		// Use reflection to invoke the method.
		try {
			ReflectionUtils.makeAccessible(method);
			return method.invoke(target, args);
		}
		catch (InvocationTargetException ex) {
			// Invoked method threw a checked exception.
			// We must rethrow it. The client won't see the interceptor.
			throw ex.getTargetException();
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" +
					method + "] on target [" + target + "]", ex);
		}
		catch (IllegalAccessException ex) {
			throw new AopInvocationException("Could not access method [" + method + "]", ex);
		}
	}

}
