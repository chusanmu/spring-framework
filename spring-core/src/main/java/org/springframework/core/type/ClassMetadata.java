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

import org.springframework.lang.Nullable;

/**
 * TODO: class的元信息，存了class的一系列信息
 * Interface that defines abstract metadata of a specific class,
 * in a form that does not require that class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 */
public interface ClassMetadata {

	/**
	 * TODO: 返回当前class的名称
	 * Return the name of the underlying class.
	 */
	String getClassName();

	/**
	 * TODO: 判断当前class是否是接口
	 * Return whether the underlying class represents an interface.
	 */
	boolean isInterface();

	/**
	 * TODO: 当前class是否是一个注解
	 * Return whether the underlying class represents an annotation.
	 * @since 4.1
	 */
	boolean isAnnotation();

	/**
	 * TODO: 当前class是否是抽象的
	 * Return whether the underlying class is marked as abstract.
	 */
	boolean isAbstract();

	/**
	 * TODO: 判断该类 不是接口 或者 不是抽象类
	 * Return whether the underlying class represents a concrete class,
	 * i.e. neither an interface nor an abstract class.
	 */
	boolean isConcrete();

	/**
	 * TODO: 当前class是否是final的
	 * Return whether the underlying class is marked as 'final'.
	 */
	boolean isFinal();

	/**
	 * TODO: 判断是嵌套类还是独立的一个类
	 * Determine whether the underlying class is independent, i.e. whether
	 * it is a top-level class or a nested class (static inner class) that
	 * can be constructed independently from an enclosing class.
	 */
	boolean isIndependent();

	/**
	 * TODO: 返回是否是个内部类 或者 是个 方法内部类，如果返回false，表示是个独立的类
	 * Return whether the underlying class is declared within an enclosing
	 * class (i.e. the underlying class is an inner/nested class or a
	 * local class within a method).
	 * <p>If this method returns {@code false}, then the underlying
	 * class is a top-level class.
	 */
	boolean hasEnclosingClass();

	/**
	 * Return the name of the enclosing class of the underlying class,
	 * or {@code null} if the underlying class is a top-level class.
	 */
	@Nullable
	String getEnclosingClassName();

	/**
	 * TODO: 是否有父类
	 * Return whether the underlying class has a super class.
	 */
	boolean hasSuperClass();

	/**
	 * TODO: 返回父类的类名
	 * Return the name of the super class of the underlying class,
	 * or {@code null} if there is no super class defined.
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * TODO: 返回所有接口的名称
	 * Return the names of all interfaces that the underlying class
	 * implements, or an empty array if there are none.
	 */
	String[] getInterfaceNames();

	/**
	 * TODO: 返回所有内部类的名字
	 * Return the names of all classes declared as members of the class represented by
	 * this ClassMetadata object. This includes public, protected, default (package)
	 * access, and private classes and interfaces declared by the class, but excludes
	 * inherited classes and interfaces. An empty array is returned if no member classes
	 * or interfaces exist.
	 * @since 3.1
	 */
	String[] getMemberClassNames();

}
