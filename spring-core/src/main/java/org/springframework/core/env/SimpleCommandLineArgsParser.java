/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.core.env;

/**
 * Parses a {@code String[]} of command line arguments in order to populate a
 * {@link CommandLineArgs} object.
 *
 * <h3>Working with option arguments</h3>
 * Option arguments must adhere to the exact syntax:
 * <pre class="code">--optName[=optValue]</pre>
 * That is, options must be prefixed with "{@code --}", and may or may not specify a value.
 * If a value is specified, the name and value must be separated <em>without spaces</em>
 * by an equals sign ("=").
 *
 * <h4>Valid examples of option arguments</h4>
 * <pre class="code">
 * --foo
 * --foo=bar
 * --foo="bar then baz"
 * --foo=bar,baz,biz</pre>
 *
 * <h4>Invalid examples of option arguments</h4>
 * <pre class="code">
 * -foo
 * --foo bar
 * --foo = bar
 * --foo=bar --foo=baz --foo=biz</pre>
 *
 * <h3>Working with non-option arguments</h3>
 * Any and all arguments specified at the command line without the "{@code --}" option
 * prefix will be considered as "non-option arguments" and made available through the
 * {@link CommandLineArgs#getNonOptionArgs()} method.
 *
 * @author Chris Beams
 * @since 3.1
 */
class SimpleCommandLineArgsParser {

	/**
	 * Parse the given {@code String} array based on the rules described {@linkplain
	 * SimpleCommandLineArgsParser above}, returning a fully-populated
	 * {@link CommandLineArgs} object.
	 * @param args command line arguments, typically from a {@code main()} method
	 */
	public CommandLineArgs parse(String... args) {
		CommandLineArgs commandLineArgs = new CommandLineArgs();
		// TODO: 把所有的参数进行遍历
		for (String arg : args) {
			// TODO: 如果有--开头的，就截取一下，比如 --server.port = 8080
			if (arg.startsWith("--")) {
				String optionText = arg.substring(2, arg.length());
				String optionName;
				String optionValue = null;
				// TODO: 到这 optionText : server.port = 8080
				if (optionText.contains("=")) {
					// 有等号, 进行等号前截取
					optionName = optionText.substring(0, optionText.indexOf('='));
					// TODO: 进行等号后的值的提取
					optionValue = optionText.substring(optionText.indexOf('=')+1, optionText.length());
				}
				else {
					// TODO: 没等号 直接把你传进来的value设置为optionName
					optionName = optionText;
				}
				if (optionName.isEmpty() || (optionValue != null && optionValue.isEmpty())) {
					throw new IllegalArgumentException("Invalid argument syntax: " + arg);
				}
				commandLineArgs.addOptionArg(optionName, optionValue);
			}
			else {
				commandLineArgs.addNonOptionArg(arg);
			}
		}
		return commandLineArgs;
	}

}
