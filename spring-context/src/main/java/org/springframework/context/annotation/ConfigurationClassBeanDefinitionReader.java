/*
 * Copyright 2002-2020 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {

	private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

	private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private final BeanDefinitionRegistry registry;

	private final SourceExtractor sourceExtractor;

	private final ResourceLoader resourceLoader;

	private final Environment environment;

	private final BeanNameGenerator importBeanNameGenerator;

	private final ImportRegistry importRegistry;

	private final ConditionEvaluator conditionEvaluator;


	/**
	 * Create a new {@link ConfigurationClassBeanDefinitionReader} instance
	 * that will be used to populate the given {@link BeanDefinitionRegistry}.
	 */
	ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
			ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
			ImportRegistry importRegistry) {

		this.registry = registry;
		this.sourceExtractor = sourceExtractor;
		this.resourceLoader = resourceLoader;
		this.environment = environment;
		this.importBeanNameGenerator = importBeanNameGenerator;
		this.importRegistry = importRegistry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}


	/**
	 * Read {@code configurationModel}, registering bean definitions
	 * with the registry based on its contents.
	 */
	public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
		// TODO: TrackedConditionEvaluator 是个内部类,是去解析@Conditional相关注解的，借助了conditionEvaluator去计算处理，主要是看要不要shouldSkip()
		TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
		// TODO: 对每个@Configuration 类文件，做遍历，所以@Config配置文件的顺序还是很重要的
		for (ConfigurationClass configClass : configurationModel) {
			loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
		}
	}

	/**
	 * TODO: 用来解析每一个已经解析好的Configuration配置文件
	 * TODO: 从指定的一个配置类ConfigurationClass中提取bean定义信息并注册bean定义到bean容器
	 * 1.配置类本身要注册为bean定义，2.配置类中的@Bean注解方法要注册为配置类
	 * Read a particular {@link ConfigurationClass}, registering bean definitions
	 * for the class itself and all of its {@link Bean} methods.
	 */
	private void loadBeanDefinitionsForConfigurationClass(
			ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {
		// TODO: 这里判断是否需要跳过，与之前解析@Configuration判断是否跳过的逻辑是相同的，借助了conditionEvaluator
		if (trackedConditionEvaluator.shouldSkip(configClass)) {
			String beanName = configClass.getBeanName();
			if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
				this.registry.removeBeanDefinition(beanName);
			}
			this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
			return;
		}
		// TODO: 最先处理注册@Import进来的bean定义，判断依据是 configClass.isImported()
		if (configClass.isImported()) {
			registerBeanDefinitionForImportedConfigurationClass(configClass);
		}
		// TODO: 第二步开始处理 @Bean的方式进来的，方法访问权限无要求，private无所谓，static的也可以
		for (BeanMethod beanMethod : configClass.getBeanMethods()) {
			loadBeanDefinitionsForBeanMethod(beanMethod);
		}
		// TODO: 注册importedResources进来的bean们
		loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());
		// TODO: 执行ImportBeanDefinitionRegistrar#registerBeanDefinitions() 注册bean定义信息，也就是此处执行ImportBeanDefinitionRegistrar的接口方法
		loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
	}

	/**
	 * TODO: 去注册 @Import 导进来的BeanDefinition
	 * Register the {@link Configuration} class itself as a bean definition.
	 */
	private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
		// TODO: 把它的metadata 拿到
		AnnotationMetadata metadata = configClass.getMetadata();
		// TODO: 创建一个AnnotatedGenericBeanDefinition
		AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

		ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
		configBeanDef.setScope(scopeMetadata.getScopeName());
		// TODO: 生成bean的名称
		String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
		// TODO: 处理beanDefinition默认的一些属性
		AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
		// TODO: 判断是否需要产生作用域代理类
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		// TODO: 然后直接 进行注册beanDefinition
		this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
		configClass.setBeanName(configBeanName);

		if (logger.isTraceEnabled()) {
			logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
		}
	}

	/**
	 * TODO: 读取BeanMethod，然后通过register注册beanDefinition进容器
	 * Read the given {@link BeanMethod}, registering bean definitions
	 * with the BeanDefinitionRegistry based on its contents.
	 */
	@SuppressWarnings("deprecation")  // for RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE
	private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
		// TODO: 把当前method的configClass拿到，主要是一些元信息
		ConfigurationClass configClass = beanMethod.getConfigurationClass();
		MethodMetadata metadata = beanMethod.getMetadata();
		String methodName = metadata.getMethodName();

		// Do we need to mark the bean as skipped by its condition?
		// TODO: 判断当前@BeanMethod是否应该跳过，阶段是，在注册Bean的时候 去生效 然后判断
		if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
			// TODO: 如果需要跳过，则加到skippedBeanMethods去
			configClass.skippedBeanMethods.add(methodName);
			return;
		}
		// TODO: 这里不太理解，既然判断完是否跳过了，然后直接return了，为啥还要再判断呢？不是很理解？感觉是多余的
		if (configClass.skippedBeanMethods.contains(methodName)) {
			return;
		}
		// TODO: 拿到@Bean元注解的信息
		AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
		Assert.state(bean != null, "No @Bean annotation attributes");

		// Consider name and any aliases
		// TODO: 把bean的名称拿到
		List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
		// TODO: 注意，这里先去看看@Bean 中name是否是空的，如果非空，把第一个名字拿到，否则拿到的是方法名
		String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

		// Register aliases even when overridden
		// TODO: 上面移除了bean的名称，那么剩下的就是别名喽，开始注册别名
		for (String alias : names) {
			this.registry.registerAlias(beanName, alias);
		}

		// Has this effectively been overridden before (e.g. via XML)?
		// TODO: 简单看来，是查看bean是否重名，查看你配置的这个bean是否和它所属的配置文件重名
		if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
			// TODO: 如果你这个bean和声明此bean的配置类名称相同，ok，那就直接抛出异常吧
			if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
				throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
						beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
						"' clashes with bean name for containing configuration class; please make those names unique!");
			}
			return;
		}
		// TODO: 此处可以看出来，通过@Bean添加进来的beanDefinition的类型为 ConfigurationClassBeanDefinition
		ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata);
		beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

		// TODO: 如果是静态方法，静态方法不依赖实例化配置类
		if (metadata.isStatic()) {
			// static @Bean method
			beanDef.setBeanClassName(configClass.getMetadata().getClassName());
			beanDef.setFactoryMethodName(methodName);
		}
		else {
			// TODO: 实例方法
			// instance @Bean method
			beanDef.setFactoryBeanName(configClass.getBeanName());
			beanDef.setUniqueFactoryMethodName(methodName);
		}
		// TODO: 设置注入方式
		beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
		beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.
				SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);
		// TODO: 处理一些公共的注解属性，Lazy Role Primary
		AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

		Autowire autowire = bean.getEnum("autowire");
		if (autowire.isAutowire()) {
			beanDef.setAutowireMode(autowire.value());
		}

		boolean autowireCandidate = bean.getBoolean("autowireCandidate");
		if (!autowireCandidate) {
			beanDef.setAutowireCandidate(false);
		}
		// TODO: 获得初始化方法
		String initMethodName = bean.getString("initMethod");
		// TODO: 如果存在初始化方法，则设置初始化方法
		if (StringUtils.hasText(initMethodName)) {
			beanDef.setInitMethodName(initMethodName);
		}
		// TODO: 设置销毁方法
		String destroyMethodName = bean.getString("destroyMethod");
		beanDef.setDestroyMethodName(destroyMethodName);

		// Consider scoping
		// TODO: 设置作用于代理模式
		ScopedProxyMode proxyMode = ScopedProxyMode.NO;
		AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
		if (attributes != null) {
			// TODO: 设置bean的作用域，默认是单例的
			beanDef.setScope(attributes.getString("value"));
			// TODO: 获得bean作用域的代理模式
			proxyMode = attributes.getEnum("proxyMode");
			// TODO: 如果是默认的，那就是不需要代理
			if (proxyMode == ScopedProxyMode.DEFAULT) {
				proxyMode = ScopedProxyMode.NO;
			}
		}

		// Replace the original bean definition with the target one, if necessary
		BeanDefinition beanDefToRegister = beanDef;
		// TODO: 说明需要进行代理
		if (proxyMode != ScopedProxyMode.NO) {
			// TODO: 创建一个代理的beanDefinitionHolder
			BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
					new BeanDefinitionHolder(beanDef, beanName), this.registry,
					proxyMode == ScopedProxyMode.TARGET_CLASS);
			// TODO: 然后从BeanDefinitionHolder中把beanDefinition拿到，然后生成一个ConfigurationClassBeanDefinition
			beanDefToRegister = new ConfigurationClassBeanDefinition(
					(RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata);
		}

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("Registering bean definition for @Bean method %s.%s()",
					configClass.getMetadata().getClassName(), beanName));
		}
		// TODO: 注册beanDefinition，注册到注册中心map中
		this.registry.registerBeanDefinition(beanName, beanDefToRegister);
	}

	protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
		// TODO: 判断注册器中 是否包含了这个名称的beanDefinition，如果不包含返回false
		if (!this.registry.containsBeanDefinition(beanName)) {
			return false;
		}
		// TODO: 已经存在以这个名字命名的beanDefinition了
		BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

		// Is the existing bean definition one that was created from a configuration class?
		// -> allow the current bean method to override, since both are at second-pass level.
		// However, if the bean method is an overloaded case on the same configuration class,
		// preserve the existing bean definition.
		// TODO: 如果你的这个beanDefinition也是通过@Bean注解加进来的，然后会进一步去判断
		if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
			ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
			// TODO: 会去判断啥呢？会去判断这个@Bean所属方法的类名是否一样，所以当一个configuration里面使用@Bean声明两个bean的时候，同名的话，第一个生效，第二个就直接返回了，不会再去注册beanDefinition了
			return ccbd.getMetadata().getClassName().equals(
					beanMethod.getConfigurationClass().getMetadata().getClassName());
		}

		// A bean definition resulting from a component scan can be silently overridden
		// by an @Bean method, as of 4.2...
		// TODO: 如果已经存在的beanDefinition是通过@ComponentScan扫描进来的，不管它，直接返回false
		if (existingBeanDef instanceof ScannedGenericBeanDefinition) {
			return false;
		}

		// Has the existing bean definition bean marked as a framework-generated bean?
		// -> allow the current bean method to override it, since it is application-level
		// TODO: 如果已经存在的这个bean被标识为非应用程序类型的bean，那么允许应用类型的bean去覆盖它，这里直接返回false, 不会return掉
		if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
			return false;
		}

		// At this point, it's a top-level override (probably XML), just having been parsed
		// before configuration class processing kicks in...
		// TODO: 如果你的beanFactory配置了不允许beanDefinition覆盖，那么肯定就直接抛出异常，终止程序了。
		if (this.registry instanceof DefaultListableBeanFactory &&
				!((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding()) {
			throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
					beanName, "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef);
		}
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
					"already exists. This top-level bean definition is considered as an override.",
					beanMethod, beanName));
		}
		// TODO: 其他情况，直接return true，比如，你配置的这个方法bean的名称和配置类的名称相同了，这时候，可能就直接返回true了
		return true;
	}

	/**
	 * TODO: 主要处理 @ImportResource导入进来的资源，比如可以使用xml方式来配置bean，甚至可以使用properties方式去配置bean，然后指定一个beanDefinitionReader去读取相应的配置文件，然后进行注册beanDefinition
	 * TODO: 一般不这样玩，用的比较少，而且可读性比较差
	 * @param importedResources
	 */
	private void loadBeanDefinitionsFromImportedResources(
			Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

		Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();

		importedResources.forEach((resource, readerClass) -> {
			// Default reader selection necessary?
			if (BeanDefinitionReader.class == readerClass) {
				if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
					// When clearly asking for Groovy, that's what they'll get...
					readerClass = GroovyBeanDefinitionReader.class;
				}
				else {
					// Primarily ".xml" files but for any other extension as well
					readerClass = XmlBeanDefinitionReader.class;
				}
			}

			BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
			if (reader == null) {
				try {
					// Instantiate the specified BeanDefinitionReader
					reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
					// Delegate the current ResourceLoader to it if possible
					if (reader instanceof AbstractBeanDefinitionReader) {
						AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
						abdr.setResourceLoader(this.resourceLoader);
						abdr.setEnvironment(this.environment);
					}
					readerInstanceCache.put(readerClass, reader);
				}
				catch (Throwable ex) {
					throw new IllegalStateException(
							"Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
				}
			}

			// TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
			reader.loadBeanDefinitions(resource);
		});
	}

	private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
		// TODO: 直接挨个的遍历 然后调用 ImportBeanDefinitionRegistrar 它的 registerBeanDefinition 方法
		registrars.forEach((registrar, metadata) ->
				registrar.registerBeanDefinitions(metadata, this.registry));
	}


	/**
	 * TODO: 该类负责将@Bean注解的方法 转换为对应的 ConfigurationClassBeanDefinition类，非常的重要
	 * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
	 * was created from a configuration class as opposed to any other configuration source.
	 * Used in bean overriding cases where it's necessary to determine whether the bean
	 * definition was created externally.
	 */
	@SuppressWarnings("serial")
	private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

		private final AnnotationMetadata annotationMetadata;

		private final MethodMetadata factoryMethodMetadata;

		public ConfigurationClassBeanDefinition(ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
			setResource(configClass.getResource());
			setLenientConstructorResolution(false);
		}

		public ConfigurationClassBeanDefinition(
				RootBeanDefinition original, ConfigurationClass configClass, MethodMetadata beanMethodMetadata) {
			super(original);
			this.annotationMetadata = configClass.getMetadata();
			this.factoryMethodMetadata = beanMethodMetadata;
		}

		private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
			super(original);
			this.annotationMetadata = original.annotationMetadata;
			this.factoryMethodMetadata = original.factoryMethodMetadata;
		}

		@Override
		public AnnotationMetadata getMetadata() {
			return this.annotationMetadata;
		}

		@Override
		public MethodMetadata getFactoryMethodMetadata() {
			return this.factoryMethodMetadata;
		}

		@Override
		public boolean isFactoryMethod(Method candidate) {
			return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate));
		}

		@Override
		public ConfigurationClassBeanDefinition cloneBeanDefinition() {
			return new ConfigurationClassBeanDefinition(this);
		}
	}


	/**
	 * Evaluate {@code @Conditional} annotations, tracking results and taking into
	 * account 'imported by'.
	 */
	private class TrackedConditionEvaluator {

		private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

		public boolean shouldSkip(ConfigurationClass configClass) {
			Boolean skip = this.skipped.get(configClass);
			if (skip == null) {
				if (configClass.isImported()) {
					boolean allSkipped = true;
					for (ConfigurationClass importedBy : configClass.getImportedBy()) {
						if (!shouldSkip(importedBy)) {
							allSkipped = false;
							break;
						}
					}
					if (allSkipped) {
						// The config classes that imported this one were all skipped, therefore we are skipped...
						skip = true;
					}
				}
				if (skip == null) {
					skip = conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN);
				}
				this.skipped.put(configClass, skip);
			}
			return skip;
		}
	}

}
