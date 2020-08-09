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

package org.springframework.context.expression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanExpressionException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * TODO: 语言解析器的标准实现，支持解析spel语言
 * Standard implementation of the
 * {@link org.springframework.beans.factory.config.BeanExpressionResolver}
 * interface, parsing and evaluating Spring EL using Spring's expression module.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.expression.ExpressionParser
 * @see org.springframework.expression.spel.standard.SpelExpressionParser
 * @see org.springframework.expression.spel.support.StandardEvaluationContext
 */
public class StandardBeanExpressionResolver implements BeanExpressionResolver {

	/**
	 * TODO: spel是支持自定义前缀，后缀的，此处保持了和Spel默认值的统一
	 */
	/** Default expression prefix: "#{". */
	public static final String DEFAULT_EXPRESSION_PREFIX = "#{";

	/** Default expression suffix: "}". */
	public static final String DEFAULT_EXPRESSION_SUFFIX = "}";


	private String expressionPrefix = DEFAULT_EXPRESSION_PREFIX;

	private String expressionSuffix = DEFAULT_EXPRESSION_SUFFIX;

	private ExpressionParser expressionParser;
	/**
	 * TODO: 每个表达式都对应一个Expression，这样就不用重复解析了
	 */
	private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(256);
	/**
	 * TODO: 每个beanExpressionContext都对应一着一个取值上下文
	 */
	private final Map<BeanExpressionContext, StandardEvaluationContext> evaluationCache = new ConcurrentHashMap<>(8);

	/**
	 * TODO: 匿名内部类，解析上下文
	 */
	private final ParserContext beanExpressionParserContext = new ParserContext() {
		@Override
		public boolean isTemplate() {
			return true;
		}
		@Override
		public String getExpressionPrefix() {
			return expressionPrefix;
		}
		@Override
		public String getExpressionSuffix() {
			return expressionSuffix;
		}
	};


	/**
	 * TODO: 空构造函数，默认就是使用的SpelExpressionParser
	 * Create a new {@code StandardBeanExpressionResolver} with default settings.
	 */
	public StandardBeanExpressionResolver() {
		this.expressionParser = new SpelExpressionParser();
	}

	/**
	 * Create a new {@code StandardBeanExpressionResolver} with the given bean class loader,
	 * using it as the basis for expression compilation.
	 * @param beanClassLoader the factory's bean class loader
	 */
	public StandardBeanExpressionResolver(@Nullable ClassLoader beanClassLoader) {
		this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(null, beanClassLoader));
	}


	/**
	 * Set the prefix that an expression string starts with.
	 * The default is "#{".
	 * @see #DEFAULT_EXPRESSION_PREFIX
	 */
	public void setExpressionPrefix(String expressionPrefix) {
		Assert.hasText(expressionPrefix, "Expression prefix must not be empty");
		this.expressionPrefix = expressionPrefix;
	}

	/**
	 * Set the suffix that an expression string ends with.
	 * The default is "}".
	 * @see #DEFAULT_EXPRESSION_SUFFIX
	 */
	public void setExpressionSuffix(String expressionSuffix) {
		Assert.hasText(expressionSuffix, "Expression suffix must not be empty");
		this.expressionSuffix = expressionSuffix;
	}

	/**
	 * Specify the EL parser to use for expression parsing.
	 * <p>Default is a {@link org.springframework.expression.spel.standard.SpelExpressionParser},
	 * compatible with standard Unified EL style expression syntax.
	 */
	public void setExpressionParser(ExpressionParser expressionParser) {
		Assert.notNull(expressionParser, "ExpressionParser must not be null");
		this.expressionParser = expressionParser;
	}


	@Override
	@Nullable
	public Object evaluate(@Nullable String value, BeanExpressionContext evalContext) throws BeansException {
		if (!StringUtils.hasLength(value)) {
			return value;
		}
		try {
			Expression expr = this.expressionCache.get(value);
			if (expr == null) {
				expr = this.expressionParser.parseExpression(value, this.beanExpressionParserContext);
				this.expressionCache.put(value, expr);
			}
			// TODO: 构建getValue计算时的执行上下文
			StandardEvaluationContext sec = this.evaluationCache.get(evalContext);
			if (sec == null) {
				sec = new StandardEvaluationContext(evalContext);
				// TODO: 此处新增了4个，加上一个默认的，所以一共就有5个属性访问器了
				// TODO: 这样spel就能访问beanFactory, map environment扥组件了
				// TODO: BeanExpressionContextAccessor 表示调用bean的方法 最终执行者为beanExpressionContext，它持有beanFactory的引用
				// TODO: 如果是单纯的bean注入，那么最终使用的也是beanExpressionContextAccessor
				sec.addPropertyAccessor(new BeanExpressionContextAccessor());
				sec.addPropertyAccessor(new BeanFactoryAccessor());
				sec.addPropertyAccessor(new MapAccessor());
				sec.addPropertyAccessor(new EnvironmentAccessor());
				// TODO: setBeanResolver不是接口方法，仅仅辅助StandardEvaluationContext去获取bean
				sec.setBeanResolver(new BeanFactoryResolver(evalContext.getBeanFactory()));
				sec.setTypeLocator(new StandardTypeLocator(evalContext.getBeanFactory().getBeanClassLoader()));
				// TODO: 若conversionService不为null,就使用工厂的，否则就使用spel里默认的DefaultConverterService那个
				// TODO: 最后包装成TypeConverter给set进去
				ConversionService conversionService = evalContext.getBeanFactory().getConversionService();
				if (conversionService != null) {
					sec.setTypeConverter(new StandardTypeConverter(conversionService));
				}
				// TODO: 这是一个空方法,若我们自己要自定义BeanExpressionResolver，完全可以继承自StandardBeanExpressionResolver
				customizeEvaluationContext(sec);
				this.evaluationCache.put(evalContext, sec);
			}
			return expr.getValue(sec);
		}
		catch (Throwable ex) {
			throw new BeanExpressionException("Expression parsing failed", ex);
		}
	}

	/**
	 * Template method for customizing the expression evaluation context.
	 * <p>The default implementation is empty.
	 */
	protected void customizeEvaluationContext(StandardEvaluationContext evalContext) {
	}

}
