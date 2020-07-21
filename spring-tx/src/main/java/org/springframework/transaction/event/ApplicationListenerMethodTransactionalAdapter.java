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

package org.springframework.transaction.event;

import java.lang.reflect.Method;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.context.event.EventListener;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * TODO: 它是 @TransactionalEventListener 的适配器,继承自ApplicationListenerMethodAdapter
 * {@link GenericApplicationListener} adapter that delegates the processing of
 * an event to a {@link TransactionalEventListener} annotated method. Supports
 * the exact same features as any regular {@link EventListener} annotated method
 * but is aware of the transactional context of the event publisher.
 *
 * <p>Processing of {@link TransactionalEventListener} is enabled automatically
 * when Spring's transaction management is enabled. For other cases, registering
 * a bean of type {@link TransactionalEventListenerFactory} is required.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 * @see ApplicationListenerMethodAdapter
 * @see TransactionalEventListener
 */
class ApplicationListenerMethodTransactionalAdapter extends ApplicationListenerMethodAdapter {

	private final TransactionalEventListener annotation;


	public ApplicationListenerMethodTransactionalAdapter(String beanName, Class<?> targetClass, Method method) {
		// TODO: 交给父类去做，去拿一些值
		super(beanName, targetClass, method);
		// TODO: 我只查找TransactionalEventListener 这个注解
		TransactionalEventListener ann = AnnotatedElementUtils.findMergedAnnotation(method, TransactionalEventListener.class);
		if (ann == null) {
			throw new IllegalStateException("No TransactionalEventListener annotation found on method: " + method);
		}
		this.annotation = ann;
	}


	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		// TODO: 如果存在事务，那就注册一个同步器 进去
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronization transactionSynchronization = createTransactionSynchronization(event);
			// TODO: 注册了一个事务同步器，在事务成功提交之后 会进行一个回调
			TransactionSynchronizationManager.registerSynchronization(transactionSynchronization);
		}
		// TODO: 如果fallbackExecution 为 true，那就表示即使没有事务，也会执行handler
		else if (this.annotation.fallbackExecution()) {
			if (this.annotation.phase() == TransactionPhase.AFTER_ROLLBACK && logger.isWarnEnabled()) {
				logger.warn("Processing " + event + " as a fallback execution on AFTER_ROLLBACK phase");
			}
			processEvent(event);
		}
		else {
			// No transactional event execution at all
			// TODO: 若没有事务，输出一个debug信息，表示这个监听器没有执行
			if (logger.isDebugEnabled()) {
				logger.debug("No transaction is active - skipping " + event);
			}
		}
	}

	/**
	 * TODO: 创建一个内部类
	 * @param event
	 * @return
	 */
	private TransactionSynchronization createTransactionSynchronization(ApplicationEvent event) {
		return new TransactionSynchronizationEventAdapter(this, event, this.annotation.phase());
	}

	/**
	 * TODO: 内部类，它是一个 TransactionSynchronization 同步器
	 */
	private static class TransactionSynchronizationEventAdapter extends TransactionSynchronizationAdapter {

		private final ApplicationListenerMethodAdapter listener;

		private final ApplicationEvent event;

		private final TransactionPhase phase;

		public TransactionSynchronizationEventAdapter(ApplicationListenerMethodAdapter listener,
				ApplicationEvent event, TransactionPhase phase) {

			this.listener = listener;
			this.event = event;
			this.phase = phase;
		}

		/**
		 * TODO: 它的order由监听器去决定
		 * @return
		 */
		@Override
		public int getOrder() {
			return this.listener.getOrder();
		}


		/* ---------------- 下面几个方法都是委托给了listener来真正的执行处理，来执行最终处理逻辑 -------------- */
		/* ---------------- 也就是解析classes, condition, 执行方法体等等 -------------- */

		@Override
		public void beforeCommit(boolean readOnly) {
			if (this.phase == TransactionPhase.BEFORE_COMMIT) {
				processEvent();
			}
		}

		/**
		 * TODO: 此处接合 status 和 phase判断是否应该执行
		 * TODO: TransactionPhase.AFTER_COMMIT也是放在了此处执行的，只是它结合了status进行判断而已
		 * @param status
		 */
		@Override
		public void afterCompletion(int status) {
			// TODO: 事务提交后执行啊
			if (this.phase == TransactionPhase.AFTER_COMMIT && status == STATUS_COMMITTED) {
				processEvent();
			}
			else if (this.phase == TransactionPhase.AFTER_ROLLBACK && status == STATUS_ROLLED_BACK) {
				processEvent();
			}
			else if (this.phase == TransactionPhase.AFTER_COMPLETION) {
				processEvent();
			}
		}

		protected void processEvent() {
			this.listener.processEvent(this.event);
		}
	}

}
