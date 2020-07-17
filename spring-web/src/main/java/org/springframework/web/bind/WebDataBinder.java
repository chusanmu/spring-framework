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

package org.springframework.web.bind;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.core.CollectionFactory;
import org.springframework.lang.Nullable;
import org.springframework.validation.DataBinder;
import org.springframework.web.multipart.MultipartFile;

/**
 * TODO: Controller方法的参数类型可以是基本类型，也可以是封装后的普通的Java类型，若这个普通Java类型没有声明任何注解，则意味着它的每一个属性都需要到Request中去查找对应的请求参数
 * TODO: 1. webDataBinder支持对属性以_打头的默认值处理，自动挡，能够自动处理所有的bool, map等
 *       2. 支持对属性名!打头的默认值处理(手动挡, 需要手动给某个属性赋默认值，自己控制的灵活性很高)
 *       3. 提供方法，支持把MultipartFile绑定到JavaBean属性上
 * Special {@link DataBinder} for data binding from web request parameters
 * to JavaBean objects. Designed for web environments, but not dependent on
 * the Servlet API; serves as base class for more specific DataBinder variants,
 * such as {@link org.springframework.web.bind.ServletRequestDataBinder}.
 *
 * <p>Includes support for field markers which address a common problem with
 * HTML checkboxes and select options: detecting that a field was part of
 * the form, but did not generate a request parameter because it was empty.
 * A field marker allows to detect that state and reset the corresponding
 * bean property accordingly. Default values, for parameters that are otherwise
 * not present, can specify a value for the field other then empty.
 *
 * @author Juergen Hoeller
 * @author Scott Andrews
 * @author Brian Clozel
 * @since 1.2
 * @see #registerCustomEditor
 * @see #setAllowedFields
 * @see #setRequiredFields
 * @see #setFieldMarkerPrefix
 * @see #setFieldDefaultPrefix
 * @see ServletRequestDataBinder
 */
public class WebDataBinder extends DataBinder {

	/**
	 * TODO: 此字段意思是：字段标记 比如 name -> _name
	 * Default prefix that field marker parameters start with, followed by the field
	 * name: e.g. "_subscribeToNewsletter" for a field "subscribeToNewsletter".
	 * <p>Such a marker parameter indicates that the field was visible, that is,
	 * existed in the form that caused the submission. If no corresponding field
	 * value parameter was found, the field will be reset. The value of the field
	 * marker parameter does not matter in this case; an arbitrary value can be used.
	 * This is particularly useful for HTML checkboxes and select options.
	 * @see #setFieldMarkerPrefix
	 */
	public static final String DEFAULT_FIELD_MARKER_PREFIX = "_";

	/**
	 * TODO: !符号是处理默认值的，提供一个默认值代替空值
	 * Default prefix that field default parameters start with, followed by the field
	 * name: e.g. "!subscribeToNewsletter" for a field "subscribeToNewsletter".
	 * <p>Default parameters differ from field markers in that they provide a default
	 * value instead of an empty value.
	 * @see #setFieldDefaultPrefix
	 */
	public static final String DEFAULT_FIELD_DEFAULT_PREFIX = "!";

	@Nullable
	private String fieldMarkerPrefix = DEFAULT_FIELD_MARKER_PREFIX;

	@Nullable
	private String fieldDefaultPrefix = DEFAULT_FIELD_DEFAULT_PREFIX;
	/**
	 * TODO: 默认也会绑定空的文件流
	 */
	private boolean bindEmptyMultipartFiles = true;

	/* ---------------- 完全沿用父类的两个构造 -------------- */

	/**
	 * Create a new WebDataBinder instance, with default object name.
	 * @param target the target object to bind onto (or {@code null}
	 * if the binder is just used to convert a plain parameter value)
	 * @see #DEFAULT_OBJECT_NAME
	 */
	public WebDataBinder(@Nullable Object target) {
		super(target);
	}

	/**
	 * Create a new WebDataBinder instance.
	 * @param target the target object to bind onto (or {@code null}
	 * if the binder is just used to convert a plain parameter value)
	 * @param objectName the name of the target object
	 */
	public WebDataBinder(@Nullable Object target, String objectName) {
		super(target, objectName);
	}


	/**
	 * Specify a prefix that can be used for parameters that mark potentially
	 * empty fields, having "prefix + field" as name. Such a marker parameter is
	 * checked by existence: You can send any value for it, for example "visible".
	 * This is particularly useful for HTML checkboxes and select options.
	 * <p>Default is "_", for "_FIELD" parameters (e.g. "_subscribeToNewsletter").
	 * Set this to null if you want to turn off the empty field check completely.
	 * <p>HTML checkboxes only send a value when they're checked, so it is not
	 * possible to detect that a formerly checked box has just been unchecked,
	 * at least not with standard HTML means.
	 * <p>One way to address this is to look for a checkbox parameter value if
	 * you know that the checkbox has been visible in the form, resetting the
	 * checkbox if no value found. In Spring web MVC, this typically happens
	 * in a custom {@code onBind} implementation.
	 * <p>This auto-reset mechanism addresses this deficiency, provided
	 * that a marker parameter is sent for each checkbox field, like
	 * "_subscribeToNewsletter" for a "subscribeToNewsletter" field.
	 * As the marker parameter is sent in any case, the data binder can
	 * detect an empty field and automatically reset its value.
	 * @see #DEFAULT_FIELD_MARKER_PREFIX
	 */
	public void setFieldMarkerPrefix(@Nullable String fieldMarkerPrefix) {
		this.fieldMarkerPrefix = fieldMarkerPrefix;
	}

	/**
	 * Return the prefix for parameters that mark potentially empty fields.
	 */
	@Nullable
	public String getFieldMarkerPrefix() {
		return this.fieldMarkerPrefix;
	}

	/**
	 * Specify a prefix that can be used for parameters that indicate default
	 * value fields, having "prefix + field" as name. The value of the default
	 * field is used when the field is not provided.
	 * <p>Default is "!", for "!FIELD" parameters (e.g. "!subscribeToNewsletter").
	 * Set this to null if you want to turn off the field defaults completely.
	 * <p>HTML checkboxes only send a value when they're checked, so it is not
	 * possible to detect that a formerly checked box has just been unchecked,
	 * at least not with standard HTML means.  A default field is especially
	 * useful when a checkbox represents a non-boolean value.
	 * <p>The presence of a default parameter preempts the behavior of a field
	 * marker for the given field.
	 * @see #DEFAULT_FIELD_DEFAULT_PREFIX
	 */
	public void setFieldDefaultPrefix(@Nullable String fieldDefaultPrefix) {
		this.fieldDefaultPrefix = fieldDefaultPrefix;
	}

	/**
	 * Return the prefix for parameters that mark default fields.
	 */
	@Nullable
	public String getFieldDefaultPrefix() {
		return this.fieldDefaultPrefix;
	}

	/**
	 * Set whether to bind empty MultipartFile parameters. Default is "true".
	 * <p>Turn this off if you want to keep an already bound MultipartFile
	 * when the user resubmits the form without choosing a different file.
	 * Else, the already bound MultipartFile will be replaced by an empty
	 * MultipartFile holder.
	 * @see org.springframework.web.multipart.MultipartFile
	 */
	public void setBindEmptyMultipartFiles(boolean bindEmptyMultipartFiles) {
		this.bindEmptyMultipartFiles = bindEmptyMultipartFiles;
	}

	/**
	 * Return whether to bind empty MultipartFile parameters.
	 */
	public boolean isBindEmptyMultipartFiles() {
		return this.bindEmptyMultipartFiles;
	}


	/**
	 * This implementation performs a field default and marker check
	 * before delegating to the superclass binding process.
	 * @see #checkFieldDefaults
	 * @see #checkFieldMarkers
	 */
	@Override
	protected void doBind(MutablePropertyValues mpvs) {
		checkFieldDefaults(mpvs);
		checkFieldMarkers(mpvs);
		super.doBind(mpvs);
	}

	/**
	 * Check the given property values for field defaults,
	 * i.e. for fields that start with the field default prefix.
	 * <p>The existence of a field defaults indicates that the specified
	 * value should be used if the field is otherwise not present.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getFieldDefaultPrefix
	 */
	protected void checkFieldDefaults(MutablePropertyValues mpvs) {
		String fieldDefaultPrefix = getFieldDefaultPrefix();
		if (fieldDefaultPrefix != null) {
			PropertyValue[] pvArray = mpvs.getPropertyValues();
			for (PropertyValue pv : pvArray) {
				// TODO: 若你给定的PropertyValue的属性名确实是以!打头的，那就做如下处理 如果javaBean的该属性可写 && mpvs不存在去掉!后的同名属性，那就添加进来表示后续可以使用了，毕竟是默认值，没有精确的匹配
				// TODO: 然后把带!的给移除掉，其实就是可以使用!来给个默认值，比如!name 表示若找不到name这个属性时，就取它的值，也就是说你request里若有传!name 保底，也就不怕出现null值了
				if (pv.getName().startsWith(fieldDefaultPrefix)) {
					String field = pv.getName().substring(fieldDefaultPrefix.length());
					if (getPropertyAccessor().isWritableProperty(field) && !mpvs.contains(field)) {
						mpvs.add(field, pv.getValue());
					}
					mpvs.removePropertyValue(pv);
				}
			}
		}
	}

	/**
	 * TODO: 处理_的步骤,若传入的字段以_打头，JavaBean的这个属性可写 ** mpvs没有去掉_后的属性名字，getEmptyValue(field,fieldType)就是根据Type类型给定默认值
	 * Check the given property values for field markers,
	 * i.e. for fields that start with the field marker prefix.
	 * <p>The existence of a field marker indicates that the specified
	 * field existed in the form. If the property values do not contain
	 * a corresponding field value, the field will be considered as empty
	 * and will be reset appropriately.
	 * @param mpvs the property values to be bound (can be modified)
	 * @see #getFieldMarkerPrefix
	 * @see #getEmptyValue(String, Class)
	 */
	protected void checkFieldMarkers(MutablePropertyValues mpvs) {
		String fieldMarkerPrefix = getFieldMarkerPrefix();
		if (fieldMarkerPrefix != null) {
			PropertyValue[] pvArray = mpvs.getPropertyValues();
			for (PropertyValue pv : pvArray) {
				if (pv.getName().startsWith(fieldMarkerPrefix)) {
					String field = pv.getName().substring(fieldMarkerPrefix.length());
					if (getPropertyAccessor().isWritableProperty(field) && !mpvs.contains(field)) {
						Class<?> fieldType = getPropertyAccessor().getPropertyType(field);
						// TODO: 获取默认的空值
						mpvs.add(field, getEmptyValue(field, fieldType));
					}
					mpvs.removePropertyValue(pv);
				}
			}
		}
	}

	/**
	 * Determine an empty value for the specified field.
	 * <p>The default implementation delegates to {@link #getEmptyValue(Class)}
	 * if the field type is known, otherwise falls back to {@code null}.
	 * @param field the name of the field
	 * @param fieldType the type of the field
	 * @return the empty value (for most fields: {@code null})
	 */
	@Nullable
	protected Object getEmptyValue(String field, @Nullable Class<?> fieldType) {
		return (fieldType != null ? getEmptyValue(fieldType) : null);
	}

	/**
	 * Determine an empty value for the specified field.
	 * <p>The default implementation returns:
	 * <ul>
	 * <li>{@code Boolean.FALSE} for boolean fields
	 * <li>an empty array for array types
	 * <li>Collection implementations for Collection types
	 * <li>Map implementations for Map types
	 * <li>else, {@code null} is used as default
	 * </ul>
	 * @param fieldType the type of the field
	 * @return the empty value (for most fields: {@code null})
	 * @since 5.0
	 */
	@Nullable
	public Object getEmptyValue(Class<?> fieldType) {
		try {
			if (boolean.class == fieldType || Boolean.class == fieldType) {
				// Special handling of boolean property.
				return Boolean.FALSE;
			}
			else if (fieldType.isArray()) {
				// Special handling of array property.
				return Array.newInstance(fieldType.getComponentType(), 0);
			}
			else if (Collection.class.isAssignableFrom(fieldType)) {
				return CollectionFactory.createCollection(fieldType, 0);
			}
			else if (Map.class.isAssignableFrom(fieldType)) {
				return CollectionFactory.createMap(fieldType, 0);
			}
		}
		catch (IllegalArgumentException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to create default value - falling back to null: " + ex.getMessage());
			}
		}
		// Default value: null.
		return null;
	}


	/**
	 * TODO: 单独提供的方法，用于绑定MultipartFile类型的数据到JavaBean属性上，显然默认是允许MultipartFile作为Bean一个属性，参与绑定的 Map<String,List<MultipartFile>> 它的key， 一般来说就是文件们了
	 * Bind all multipart files contained in the given request, if any
	 * (in case of a multipart request). To be called by subclasses.
	 * <p>Multipart files will only be added to the property values if they
	 * are not empty or if we're configured to bind empty multipart files too.
	 * @param multipartFiles a Map of field name String to MultipartFile object
	 * @param mpvs the property values to be bound (can be modified)
	 * @see org.springframework.web.multipart.MultipartFile
	 * @see #setBindEmptyMultipartFiles
	 */
	protected void bindMultipart(Map<String, List<MultipartFile>> multipartFiles, MutablePropertyValues mpvs) {
		multipartFiles.forEach((key, values) -> {
			if (values.size() == 1) {
				MultipartFile value = values.get(0);
				if (isBindEmptyMultipartFiles() || !value.isEmpty()) {
					mpvs.add(key, value);
				}
			}
			else {
				mpvs.add(key, values);
			}
		});
	}

}
