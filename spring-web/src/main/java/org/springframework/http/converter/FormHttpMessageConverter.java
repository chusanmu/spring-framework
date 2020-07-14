/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.http.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeUtility;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * TODO: 它和form表单提交有关，form表单提交 文件下载
 * Implementation of {@link HttpMessageConverter} to read and write 'normal' HTML
 * forms and also to write (but not read) multipart data (e.g. file uploads).
 *
 * <p>In other words, this converter can read and write the
 * {@code "application/x-www-form-urlencoded"} media type as
 * {@link MultiValueMap MultiValueMap&lt;String, String&gt;} and it can also
 * write (but not read) the {@code "multipart/form-data"} media type as
 * {@link MultiValueMap MultiValueMap&lt;String, Object&gt;}.
 *
 * <p>When writing multipart data, this converter uses other
 * {@link HttpMessageConverter HttpMessageConverters} to write the respective
 * MIME parts. By default, basic converters are registered (for {@code Strings}
 * and {@code Resources}). These can be overridden through the
 * {@link #setPartConverters partConverters} property.
 *
 * <p>For example, the following snippet shows how to submit an HTML form:
 * <pre class="code">
 * RestTemplate template = new RestTemplate();
 * // AllEncompassingFormHttpMessageConverter is configured by default
 *
 * MultiValueMap&lt;String, Object&gt; form = new LinkedMultiValueMap&lt;&gt;();
 * form.add("field 1", "value 1");
 * form.add("field 2", "value 2");
 * form.add("field 2", "value 3");
 * form.add("field 3", 4);  // non-String form values supported as of 5.1.4
 * template.postForLocation("https://example.com/myForm", form);
 * </pre>
 *
 * <p>The following snippet shows how to do a file upload:
 * <pre class="code">
 * MultiValueMap&lt;String, Object&gt; parts = new LinkedMultiValueMap&lt;&gt;();
 * parts.add("field 1", "value 1");
 * parts.add("file", new ClassPathResource("myFile.jpg"));
 * template.postForLocation("https://example.com/myFileUpload", parts);
 * </pre>
 *
 * <p>Some methods in this class were inspired by
 * {@code org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter
 * @see org.springframework.util.MultiValueMap
 */
public class FormHttpMessageConverter implements HttpMessageConverter<MultiValueMap<String, ?>> {

	/**
	 * TODO: 默认使用 u8 编码
	 * The default charset used by the converter.
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	/**
	 * 默认支持的 mediaType application/x-www-form-urlencoded
	 */
	private static final MediaType DEFAULT_FORM_DATA_MEDIA_TYPE =
			new MediaType(MediaType.APPLICATION_FORM_URLENCODED, DEFAULT_CHARSET);

	/**
	 * 缓存它支持的MediaType们
	 */
	private List<MediaType> supportedMediaTypes = new ArrayList<>();

	/**
	 * TODO: 用户二进制内容的消息转换器们
	 */
	private List<HttpMessageConverter<?>> partConverters = new ArrayList<>();

	private Charset charset = DEFAULT_CHARSET;

	@Nullable
	private Charset multipartCharset;

	/**
	 * 唯一的默认的构造器
	 */
	public FormHttpMessageConverter() {
		// TODO: 默认支持的mediaType , 默认支持处理两种mediaType
		this.supportedMediaTypes.add(MediaType.APPLICATION_FORM_URLENCODED);
		this.supportedMediaTypes.add(MediaType.MULTIPART_FORM_DATA);

		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316

		// TODO: 它自己不仅仅是个转换器，还内置了3种转换器，这些消息转换器都是支持Part的，支持文件下载
		this.partConverters.add(new ByteArrayHttpMessageConverter());
		this.partConverters.add(stringHttpMessageConverter);
		this.partConverters.add(new ResourceHttpMessageConverter());
		// TODO: 为partConverters 消息转换器们设置编码
		applyDefaultCharset();
	}


	/**
	 * Set the list of {@link MediaType} objects supported by this converter.
	 */
	public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
		this.supportedMediaTypes = supportedMediaTypes;
	}

	@Override
	public List<MediaType> getSupportedMediaTypes() {
		return Collections.unmodifiableList(this.supportedMediaTypes);
	}

	/**
	 * Set the message body converters to use. These converters are used to
	 * convert objects to MIME parts.
	 */
	public void setPartConverters(List<HttpMessageConverter<?>> partConverters) {
		Assert.notEmpty(partConverters, "'partConverters' must not be empty");
		this.partConverters = partConverters;
	}

	/**
	 * Add a message body converter. Such a converter is used to convert objects
	 * to MIME parts.
	 */
	public void addPartConverter(HttpMessageConverter<?> partConverter) {
		Assert.notNull(partConverter, "'partConverter' must not be null");
		this.partConverters.add(partConverter);
	}

	/**
	 * Set the default character set to use for reading and writing form data when
	 * the request or response Content-Type header does not explicitly specify it.
	 * <p>As of 4.3, this is also used as the default charset for the conversion
	 * of text bodies in a multipart request.
	 * <p>As of 5.0 this is also used for part headers including
	 * "Content-Disposition" (and its filename parameter) unless (the mutually
	 * exclusive) {@link #setMultipartCharset} is also set, in which case part
	 * headers are encoded as ASCII and <i>filename</i> is encoded with the
	 * "encoded-word" syntax from RFC 2047.
	 * <p>By default this is set to "UTF-8".
	 */
	public void setCharset(@Nullable Charset charset) {
		if (charset != this.charset) {
			this.charset = (charset != null ? charset : DEFAULT_CHARSET);
			applyDefaultCharset();
		}
	}

	/**
	 * Apply the configured charset as a default to registered part converters.
	 */
	private void applyDefaultCharset() {
		for (HttpMessageConverter<?> candidate : this.partConverters) {
			if (candidate instanceof AbstractHttpMessageConverter) {
				AbstractHttpMessageConverter<?> converter = (AbstractHttpMessageConverter<?>) candidate;
				// Only override default charset if the converter operates with a charset to begin with...
				// TODO: 覆盖掉默认的编码
				if (converter.getDefaultCharset() != null) {
					converter.setDefaultCharset(this.charset);
				}
			}
		}
	}

	/**
	 * Set the character set to use when writing multipart data to encode file
	 * names. Encoding is based on the "encoded-word" syntax defined in RFC 2047
	 * and relies on {@code MimeUtility} from "javax.mail".
	 * <p>As of 5.0 by default part headers, including Content-Disposition (and
	 * its filename parameter) will be encoded based on the setting of
	 * {@link #setCharset(Charset)} or {@code UTF-8} by default.
	 * @since 4.1.1
	 * @see <a href="https://en.wikipedia.org/wiki/MIME#Encoded-Word">Encoded-Word</a>
	 */
	public void setMultipartCharset(Charset charset) {
		this.multipartCharset = charset;
	}


	@Override
	public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
		// TODO:  只支持handler的入参类型是MultiValueMap 它才会去处理
		if (!MultiValueMap.class.isAssignableFrom(clazz)) {
			return false;
		}
		// TODO: 若没有指定mediaType, 会认为是可读的
		if (mediaType == null) {
			return true;
		}
		// TODO: 它所有支持的mediaTypes拿过来
		for (MediaType supportedMediaType : getSupportedMediaTypes()) {
			// We can't read multipart....
			// TODO: 排除multipart/form-data，其他的如果存在就返回true
			if (!supportedMediaType.equals(MediaType.MULTIPART_FORM_DATA) && supportedMediaType.includes(mediaType)) {
				return true;
			}
		}
		return false;
	}


	@Override
	public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
		// TODO: 不是MultiValueMap 直接返回false
		if (!MultiValueMap.class.isAssignableFrom(clazz)) {
			return false;
		}
		// TODO: mediaType 没有设置，如果MediaType.All里面有这个类型，则返回true
		if (mediaType == null || MediaType.ALL.equals(mediaType)) {
			return true;
		}
		// TODO: 把所有支持的mediaType拿到
		for (MediaType supportedMediaType : getSupportedMediaTypes()) {
			// TODO: 如果支持写出这种mediaType,则返回true
			if (supportedMediaType.isCompatibleWith(mediaType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * TODO: 把输入数据读进来, 变成一个MultiValueMap
	 * @param clazz the type of object to return. This type must have previously been passed to the
	 * {@link #canRead canRead} method of this interface, which must have returned {@code true}.
	 * @param inputMessage the HTTP input message to read from
	 * @return
	 * @throws IOException
	 * @throws HttpMessageNotReadableException
	 */
	@Override
	public MultiValueMap<String, String> read(@Nullable Class<? extends MultiValueMap<String, ?>> clazz,
			HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
		// TODO: 拿到请求中的mediaType
		MediaType contentType = inputMessage.getHeaders().getContentType();
		// TODO: 如果contentType里面指定了编码，则也一块取出来
		Charset charset = (contentType != null && contentType.getCharset() != null ?
				contentType.getCharset() : this.charset);
		// TODO: 把body里面的内容，根据指定编码，读成string类型作为body
		String body = StreamUtils.copyToString(inputMessage.getBody(), charset);
		// TODO: 根据& 进行分割，因为此处body的内容是 name=chusen&age=22
		String[] pairs = StringUtils.tokenizeToStringArray(body, "&");
		MultiValueMap<String, String> result = new LinkedMultiValueMap<>(pairs.length);
		// TODO: 这里使用MultiValueMap来存储解析结果，为啥用MultiValueMap这个呢？因为name可能会对应多个value提交过来
		for (String pair : pairs) {
			int idx = pair.indexOf('=');
			if (idx == -1) {
				result.add(URLDecoder.decode(pair, charset.name()), null);
			}
			else {
				String name = URLDecoder.decode(pair.substring(0, idx), charset.name());
				String value = URLDecoder.decode(pair.substring(idx + 1), charset.name());
				result.add(name, value);
			}
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void write(MultiValueMap<String, ?> map, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {

		if (!isMultipart(map, contentType)) {
			writeForm((MultiValueMap<String, Object>) map, contentType, outputMessage);
		}
		else {
			writeMultipart((MultiValueMap<String, Object>) map, outputMessage);
		}
	}


	private boolean isMultipart(MultiValueMap<String, ?> map, @Nullable MediaType contentType) {
		if (contentType != null) {
			return MediaType.MULTIPART_FORM_DATA.includes(contentType);
		}
		for (List<?> values : map.values()) {
			for (Object value : values) {
				if (value != null && !(value instanceof String)) {
					return true;
				}
			}
		}
		return false;
	}

	private void writeForm(MultiValueMap<String, Object> formData, @Nullable MediaType contentType,
			HttpOutputMessage outputMessage) throws IOException {

		contentType = getMediaType(contentType);
		outputMessage.getHeaders().setContentType(contentType);

		Charset charset = contentType.getCharset();
		Assert.notNull(charset, "No charset"); // should never occur

		final byte[] bytes = serializeForm(formData, charset).getBytes(charset);
		outputMessage.getHeaders().setContentLength(bytes.length);

		if (outputMessage instanceof StreamingHttpOutputMessage) {
			StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) outputMessage;
			streamingOutputMessage.setBody(outputStream -> StreamUtils.copy(bytes, outputStream));
		}
		else {
			StreamUtils.copy(bytes, outputMessage.getBody());
		}
	}

	private MediaType getMediaType(@Nullable MediaType mediaType) {
		if (mediaType == null) {
			return DEFAULT_FORM_DATA_MEDIA_TYPE;
		}
		else if (mediaType.getCharset() == null) {
			return new MediaType(mediaType, this.charset);
		}
		else {
			return mediaType;
		}
	}

	protected String serializeForm(MultiValueMap<String, Object> formData, Charset charset) {
		StringBuilder builder = new StringBuilder();
		formData.forEach((name, values) ->
				values.forEach(value -> {
					try {
						if (builder.length() != 0) {
							builder.append('&');
						}
						builder.append(URLEncoder.encode(name, charset.name()));
						if (value != null) {
							builder.append('=');
							builder.append(URLEncoder.encode(String.valueOf(value), charset.name()));
						}
					}
					catch (UnsupportedEncodingException ex) {
						throw new IllegalStateException(ex);
					}
				}));

		return builder.toString();
	}

	private void writeMultipart(final MultiValueMap<String, Object> parts, HttpOutputMessage outputMessage)
			throws IOException {

		final byte[] boundary = generateMultipartBoundary();
		Map<String, String> parameters = new LinkedHashMap<>(2);
		if (!isFilenameCharsetSet()) {
			parameters.put("charset", this.charset.name());
		}
		parameters.put("boundary", new String(boundary, StandardCharsets.US_ASCII));

		MediaType contentType = new MediaType(MediaType.MULTIPART_FORM_DATA, parameters);
		HttpHeaders headers = outputMessage.getHeaders();
		headers.setContentType(contentType);

		if (outputMessage instanceof StreamingHttpOutputMessage) {
			StreamingHttpOutputMessage streamingOutputMessage = (StreamingHttpOutputMessage) outputMessage;
			streamingOutputMessage.setBody(outputStream -> {
				writeParts(outputStream, parts, boundary);
				writeEnd(outputStream, boundary);
			});
		}
		else {
			writeParts(outputMessage.getBody(), parts, boundary);
			writeEnd(outputMessage.getBody(), boundary);
		}
	}

	/**
	 * When {@link #setMultipartCharset(Charset)} is configured (i.e. RFC 2047,
	 * "encoded-word" syntax) we need to use ASCII for part headers or otherwise
	 * we encode directly using the configured {@link #setCharset(Charset)}.
	 */
	private boolean isFilenameCharsetSet() {
		return (this.multipartCharset != null);
	}

	private void writeParts(OutputStream os, MultiValueMap<String, Object> parts, byte[] boundary) throws IOException {
		for (Map.Entry<String, List<Object>> entry : parts.entrySet()) {
			String name = entry.getKey();
			for (Object part : entry.getValue()) {
				if (part != null) {
					writeBoundary(os, boundary);
					writePart(name, getHttpEntity(part), os);
					writeNewLine(os);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void writePart(String name, HttpEntity<?> partEntity, OutputStream os) throws IOException {
		Object partBody = partEntity.getBody();
		if (partBody == null) {
			throw new IllegalStateException("Empty body for part '" + name + "': " + partEntity);
		}
		Class<?> partType = partBody.getClass();
		HttpHeaders partHeaders = partEntity.getHeaders();
		MediaType partContentType = partHeaders.getContentType();
		for (HttpMessageConverter<?> messageConverter : this.partConverters) {
			if (messageConverter.canWrite(partType, partContentType)) {
				Charset charset = isFilenameCharsetSet() ? StandardCharsets.US_ASCII : this.charset;
				HttpOutputMessage multipartMessage = new MultipartHttpOutputMessage(os, charset);
				multipartMessage.getHeaders().setContentDispositionFormData(name, getFilename(partBody));
				if (!partHeaders.isEmpty()) {
					multipartMessage.getHeaders().putAll(partHeaders);
				}
				((HttpMessageConverter<Object>) messageConverter).write(partBody, partContentType, multipartMessage);
				return;
			}
		}
		throw new HttpMessageNotWritableException("Could not write request: no suitable HttpMessageConverter " +
				"found for request type [" + partType.getName() + "]");
	}

	/**
	 * Generate a multipart boundary.
	 * <p>This implementation delegates to
	 * {@link MimeTypeUtils#generateMultipartBoundary()}.
	 */
	protected byte[] generateMultipartBoundary() {
		return MimeTypeUtils.generateMultipartBoundary();
	}

	/**
	 * Return an {@link HttpEntity} for the given part Object.
	 * @param part the part to return an {@link HttpEntity} for
	 * @return the part Object itself it is an {@link HttpEntity},
	 * or a newly built {@link HttpEntity} wrapper for that part
	 */
	protected HttpEntity<?> getHttpEntity(Object part) {
		return (part instanceof HttpEntity ? (HttpEntity<?>) part : new HttpEntity<>(part));
	}

	/**
	 * Return the filename of the given multipart part. This value will be used for the
	 * {@code Content-Disposition} header.
	 * <p>The default implementation returns {@link Resource#getFilename()} if the part is a
	 * {@code Resource}, and {@code null} in other cases. Can be overridden in subclasses.
	 * @param part the part to determine the file name for
	 * @return the filename, or {@code null} if not known
	 */
	@Nullable
	protected String getFilename(Object part) {
		if (part instanceof Resource) {
			Resource resource = (Resource) part;
			String filename = resource.getFilename();
			if (filename != null && this.multipartCharset != null) {
				filename = MimeDelegate.encode(filename, this.multipartCharset.name());
			}
			return filename;
		}
		else {
			return null;
		}
	}


	private void writeBoundary(OutputStream os, byte[] boundary) throws IOException {
		os.write('-');
		os.write('-');
		os.write(boundary);
		writeNewLine(os);
	}

	private static void writeEnd(OutputStream os, byte[] boundary) throws IOException {
		os.write('-');
		os.write('-');
		os.write(boundary);
		os.write('-');
		os.write('-');
		writeNewLine(os);
	}

	private static void writeNewLine(OutputStream os) throws IOException {
		os.write('\r');
		os.write('\n');
	}


	/**
	 * Implementation of {@link org.springframework.http.HttpOutputMessage} used
	 * to write a MIME multipart.
	 */
	private static class MultipartHttpOutputMessage implements HttpOutputMessage {

		private final OutputStream outputStream;

		private final Charset charset;

		private final HttpHeaders headers = new HttpHeaders();

		private boolean headersWritten = false;

		public MultipartHttpOutputMessage(OutputStream outputStream, Charset charset) {
			this.outputStream = outputStream;
			this.charset = charset;
		}

		@Override
		public HttpHeaders getHeaders() {
			return (this.headersWritten ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers);
		}

		@Override
		public OutputStream getBody() throws IOException {
			writeHeaders();
			return this.outputStream;
		}

		private void writeHeaders() throws IOException {
			if (!this.headersWritten) {
				for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
					byte[] headerName = getBytes(entry.getKey());
					for (String headerValueString : entry.getValue()) {
						byte[] headerValue = getBytes(headerValueString);
						this.outputStream.write(headerName);
						this.outputStream.write(':');
						this.outputStream.write(' ');
						this.outputStream.write(headerValue);
						writeNewLine(this.outputStream);
					}
				}
				writeNewLine(this.outputStream);
				this.headersWritten = true;
			}
		}

		private byte[] getBytes(String name) {
			return name.getBytes(this.charset);
		}
	}


	/**
	 * Inner class to avoid a hard dependency on the JavaMail API.
	 */
	private static class MimeDelegate {

		public static String encode(String value, String charset) {
			try {
				return MimeUtility.encodeText(value, charset, null);
			}
			catch (UnsupportedEncodingException ex) {
				throw new IllegalStateException(ex);
			}
		}
	}

}
