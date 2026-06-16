package com.sadna.group13a.infrastructure.persistence;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Proxy;
import java.util.Set;

/**
 * Transparently wraps every repository bean (those in {@code infrastructure.RepositoryImpl})
 * in a proxy that enforces data-store connectivity before each call (issue #228).
 *
 * <p>This keeps the resilience concern in one place — no repository implementation has to
 * be modified — and only affects beans created by the Spring container, so unit tests that
 * instantiate repositories directly are unaffected.
 */
@Component
public class RepositoryAvailabilityBeanPostProcessor implements BeanPostProcessor {

    private static final String REPOSITORY_PACKAGE = "com.sadna.group13a.infrastructure.RepositoryImpl.";

    private final DatabaseConnectionManager connectionManager;

    // @Lazy avoids forcing the manager (and its dependencies) to initialise before
    // bean post-processors are ready; it is only needed at invocation time.
    public RepositoryAvailabilityBeanPostProcessor(@Lazy DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();
        if (!beanClass.getName().startsWith(REPOSITORY_PACKAGE)) {
            return bean;
        }
        Set<Class<?>> interfaces = ClassUtils.getAllInterfacesForClassAsSet(beanClass);
        if (interfaces.isEmpty()) {
            return bean;
        }
        return Proxy.newProxyInstance(
                beanClass.getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                new PersistenceAvailabilityInvocationHandler(bean, connectionManager));
    }
}
