package com.moma.di;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Bean 定义信息，描述一个 Bean 的元数据。
 */
public class BeanDefinition {

    /** Bean 名称 */
    private final String name;

    /** Bean 类型 */
    private final Class<?> beanClass;

    /** 作用域：singleton / prototype */
    private final String scope;

    /** 是否为主 Bean（@Primary） */
    private final boolean primary;

    /** 初始化方法（@PostConstruct 标注的方法名，可能为 null） */
    private final String initMethodName;

    /** 如果是由 @Bean 方法创建的，记录该方法 */
    private final Method factoryMethod;

    /** 如果是 @Bean 方法所在的配置类实例 */
    private final Object factoryBeanInstance;

    private BeanDefinition(Builder builder) {
        this.name = builder.name;
        this.beanClass = builder.beanClass;
        this.scope = builder.scope;
        this.primary = builder.primary;
        this.initMethodName = builder.initMethodName;
        this.factoryMethod = builder.factoryMethod;
        this.factoryBeanInstance = builder.factoryBeanInstance;
    }

    public String getName() { return name; }
    public Class<?> getBeanClass() { return beanClass; }
    public String getScope() { return scope; }
    public boolean isSingleton() { return "singleton".equals(scope); }
    public boolean isPrimary() { return primary; }
    public String getInitMethodName() { return initMethodName; }
    public Method getFactoryMethod() { return factoryMethod; }
    public Object getFactoryBeanInstance() { return factoryBeanInstance; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Class<?> beanClass;
        private String scope = "singleton";
        private boolean primary = false;
        private String initMethodName;
        private Method factoryMethod;
        private Object factoryBeanInstance;

        public Builder name(String name) { this.name = name; return this; }
        public Builder beanClass(Class<?> beanClass) { this.beanClass = beanClass; return this; }
        public Builder scope(String scope) { this.scope = scope; return this; }
        public Builder primary(boolean primary) { this.primary = primary; return this; }
        public Builder initMethodName(String initMethodName) { this.initMethodName = initMethodName; return this; }
        public Builder factoryMethod(Method factoryMethod) { this.factoryMethod = factoryMethod; return this; }
        public Builder factoryBeanInstance(Object factoryBeanInstance) { this.factoryBeanInstance = factoryBeanInstance; return this; }
        public BeanDefinition build() {
            Objects.requireNonNull(name, "Bean name is required");
            Objects.requireNonNull(beanClass, "Bean class is required");
            return new BeanDefinition(this);
        }
    }

    @Override
    public String toString() {
        return "BeanDefinition{" + "name='" + name + '\'' + ", class=" + beanClass.getSimpleName()
            + ", scope=" + scope + ", primary=" + primary + '}';
    }
}
