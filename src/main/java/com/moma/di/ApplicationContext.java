package com.moma.di;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 轻量级 DI 容器。对应 Spring {@code ApplicationContext}。
 *
 * <p>功能：</p>
 * <ul>
 *   <li>注解驱动：{@link Component}、{@link Configuration}、{@link Inject}、{@link Value}</li>
 *   <li>构造器注入 + 字段注入</li>
 *   <li>{@link Primary} 处理类型歧义</li>
 *   <li>{@link PostConstruct} 初始化回调</li>
 *   <li>包扫描 + 手动注册</li>
 *   <li>循环依赖检测</li>
 *   <li>{@code @Value("${key:default}")} 占位符替换</li>
 * </ul>
 */
public class ApplicationContext {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationContext.class);

    /** Bean 定义映射：name -> BeanDefinition */
    private final Map<String, BeanDefinition> beanDefinitions = new LinkedHashMap<>();

    /** 单例 Bean 实例缓存：name -> instance */
    private final Map<String, Object> singletonBeans = new ConcurrentHashMap<>();

    /** 正在创建中的 Bean（用于循环依赖检测） */
    private final Set<String> beansInCreation = ConcurrentHashMap.newKeySet();

    /** 属性源（用于 @Value 解析） */
    private Map<String, String> propertySource = new HashMap<>();

    /** 是否已刷新（初始化完成） */
    private volatile boolean refreshed = false;

    // ──────────────────────────────────────────────
    // 注册 API
    // ──────────────────────────────────────────────

    /**
     * 手动注册一个 Bean 定义。
     */
    public ApplicationContext register(BeanDefinition definition) {
        beanDefinitions.put(definition.getName(), definition);
        return this;
    }

    /**
     * 注册一个类作为 Bean（通过注解元信息）。
     */
    public ApplicationContext register(Class<?> clazz) {
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            String name = resolveBeanName(clazz, component);
            boolean primary = clazz.isAnnotationPresent(Primary.class);
            String initMethod = findPostConstruct(clazz);

            beanDefinitions.put(name, BeanDefinition.builder()
                .name(name)
                .beanClass(clazz)
                .scope(component.scope())
                .primary(primary)
                .initMethodName(initMethod)
                .build());
        }

        Configuration configuration = clazz.getAnnotation(Configuration.class);
        if (configuration != null) {
            // 注册配置类本身
            String name = Character.toLowerCase(clazz.getSimpleName().charAt(0))
                + clazz.getSimpleName().substring(1);
            if (!beanDefinitions.containsKey(name)) {
                beanDefinitions.put(name, BeanDefinition.builder()
                    .name(name)
                    .beanClass(clazz)
                    .scope("singleton")
                    .initMethodName(findPostConstruct(clazz))
                    .build());
            }
        }

        return this;
    }

    /**
     * 扫描并注册指定包下的所有 {@link Component} 和 {@link Configuration} 类。
     */
    public ApplicationContext registerPackage(String... basePackages) {
        ComponentScanner scanner = new ComponentScanner();
        Set<Class<?>> classes = scanner.scan(basePackages);
        for (Class<?> clazz : classes) {
            register(clazz);
        }
        return this;
    }

    /**
     * 设置属性源（用于 @Value 占位符解析）。
     */
    public ApplicationContext setPropertySource(Map<String, String> propertySource) {
        this.propertySource = propertySource != null ? new HashMap<>(propertySource) : new HashMap<>();
        return this;
    }

    // ──────────────────────────────────────────────
    // 刷新（初始化所有单例 Bean）
    // ──────────────────────────────────────────────

    /**
     * 刷新容器：创建所有单例 Bean 实例，执行依赖注入和初始化回调。
     */
    public void refresh() {
        LOG.info("DI 容器开始刷新，共 {} 个 Bean 定义", beanDefinitions.size());

        // 阶段1：创建配置类实例，处理 @Bean 方法
        processConfigurationBeans();

        // 阶段2：创建其他单例 Bean
        for (BeanDefinition def : beanDefinitions.values()) {
            if (def.isSingleton() && !singletonBeans.containsKey(def.getName())) {
                if (def.getFactoryMethod() == null) { // @Bean 方法已在阶段1处理
                    getBean(def.getName());
                }
            }
        }

        refreshed = true;
        LOG.info("DI 容器刷新完成，共 {} 个单例 Bean", singletonBeans.size());
    }

    /**
     * 处理 @Configuration 类中的 @Bean 方法。
     * 分为两阶段：先注册所有 Bean 定义，再创建实例，确保依赖可解析。
     */
    private void processConfigurationBeans() {
        // 先创建所有配置类实例（作为普通 Bean 创建）
        List<String> configBeanNames = beanDefinitions.values().stream()
            .filter(def -> def.getBeanClass().isAnnotationPresent(Configuration.class))
            .map(BeanDefinition::getName)
            .collect(Collectors.toList());

        for (String name : configBeanNames) {
            getBean(name);
        }

        // 阶段1：收集并注册所有 @Bean 方法的定义
        for (String name : configBeanNames) {
            Object configInstance = singletonBeans.get(name);
            if (configInstance == null) continue;

            Class<?> configClass = configInstance.getClass();
            for (Method method : configClass.getDeclaredMethods()) {
                Bean beanAnnotation = method.getAnnotation(Bean.class);
                if (beanAnnotation == null) continue;

                String beanName = beanAnnotation.name();
                if (beanName.isEmpty()) beanName = method.getName();

                // 注册 @Bean 定义（仅注册，不创建）
                beanDefinitions.put(beanName, BeanDefinition.builder()
                    .name(beanName)
                    .beanClass(method.getReturnType())
                    .scope(beanAnnotation.scope())
                    .primary(method.isAnnotationPresent(Primary.class))
                    .factoryMethod(method)
                    .factoryBeanInstance(configInstance)
                    .initMethodName(findPostConstruct(method.getReturnType()))
                    .build());
            }
        }

        // 阶段2：创建所有单例 @Bean 实例
        for (String name : configBeanNames) {
            Object configInstance = singletonBeans.get(name);
            if (configInstance == null) continue;

            Class<?> configClass = configInstance.getClass();
            for (Method method : configClass.getDeclaredMethods()) {
                Bean beanAnnotation = method.getAnnotation(Bean.class);
                if (beanAnnotation == null) continue;

                String beanName = beanAnnotation.name();
                if (beanName.isEmpty()) beanName = method.getName();

                // 跳过已创建的 Bean（可能由其他 @Bean 方法依赖创建）
                if (singletonBeans.containsKey(beanName)) continue;

                if ("singleton".equals(beanAnnotation.scope())) {
                    Object bean = invokeFactoryMethod(method, configInstance);
                    singletonBeans.put(beanName, bean);
                    invokePostConstruct(bean);
                    LOG.debug("@Bean 方法创建: {} -> {}", beanName, bean.getClass().getSimpleName());
                }
            }
        }
    }

    private Object invokeFactoryMethod(Method method, Object configInstance) {
        try {
            // 解析 @Bean 方法的参数（可能也有 @Inject 或 @Value）
            Class<?>[] paramTypes = method.getParameterTypes();
            Annotation[][] paramAnns = method.getParameterAnnotations();
            Object[] args = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                String value = findValueAnnotation(paramAnns[i]);
                if (value != null) {
                    args[i] = resolvePlaceholder(value, paramTypes[i]);
                } else {
                    args[i] = getBean(paramTypes[i]);
                }
            }

            method.setAccessible(true);
            return method.invoke(configInstance, args);
        } catch (Exception e) {
            throw new RuntimeException("执行 @Bean 方法失败: " + method.getName(), e);
        }
    }

    // ──────────────────────────────────────────────
    // 获取 Bean
    // ──────────────────────────────────────────────

    /**
     * 按名称获取 Bean。
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = beanDefinitions.get(name);
        if (def == null) {
            throw new RuntimeException("Bean 未定义: " + name);
        }

        if (def.isSingleton()) {
            // 先检查是否已有实例
            Object existing = singletonBeans.get(name);
            if (existing != null) {
                return (T) existing;
            }

            // 检查是否循环依赖
            if (beansInCreation.contains(name)) {
                throw new RuntimeException("检测到循环依赖: " + name
                    + " 当前创建链: " + beansInCreation);
            }

            beansInCreation.add(name);
            try {
                Object instance = createBeanInstance(def);
                singletonBeans.put(name, instance);
                beansInCreation.remove(name);
                return (T) instance;
            } catch (Exception e) {
                beansInCreation.remove(name);
                throw e;
            }
        } else {
            return (T) createBeanInstance(def);
        }
    }

    /**
     * 按类型获取 Bean。如果有多个匹配，返回 @Primary 标注的 Bean。
     *
     * @throws RuntimeException 如果找不到或存在多个非 Primary 匹配
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        List<Map.Entry<String, BeanDefinition>> candidates = beanDefinitions.entrySet().stream()
            .filter(e -> type.isAssignableFrom(e.getValue().getBeanClass()))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new RuntimeException("找不到类型为 " + type.getSimpleName() + " 的 Bean");
        }

        if (candidates.size() == 1) {
            return (T) getBean(candidates.get(0).getKey());
        }

        // 有多个候选：尝试 @Primary
        List<Map.Entry<String, BeanDefinition>> primary = candidates.stream()
            .filter(e -> e.getValue().isPrimary())
            .collect(Collectors.toList());

        if (primary.size() == 1) {
            return (T) getBean(primary.get(0).getKey());
        }

        if (primary.size() > 1) {
            throw new RuntimeException("类型 " + type.getSimpleName()
                + " 存在多个 @Primary Bean: " + primary.stream().map(Map.Entry::getKey).collect(Collectors.joining(", ")));
        }

        // 退而求其次：按名称匹配
        String defaultName = Character.toLowerCase(type.getSimpleName().charAt(0))
            + type.getSimpleName().substring(1);
        if (beanDefinitions.containsKey(defaultName)
            && type.isAssignableFrom(beanDefinitions.get(defaultName).getBeanClass())) {
            return (T) getBean(defaultName);
        }

        throw new RuntimeException("类型 " + type.getSimpleName()
            + " 存在多个 Bean，且无 @Primary: "
            + candidates.stream().map(Map.Entry::getKey).collect(Collectors.joining(", ")));
    }

    /**
     * 获取指定类型的 Bean 或 Supplier（如果不存在则返回 null）。
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getBeanOptional(Class<T> type) {
        try {
            return Optional.ofNullable(getBean(type));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * 检查容器是否包含指定名称的 Bean。
     */
    public boolean containsBean(String name) {
        return beanDefinitions.containsKey(name);
    }

    /**
     * 获取所有注册的 Bean 名称。
     */
    public Set<String> getBeanNames() {
        return beanDefinitions.keySet();
    }

    /**
     * 获取所有已初始化的单例 Bean 名称。
     */
    public Set<String> getSingletonBeanNames() {
        return singletonBeans.keySet();
    }

    // ──────────────────────────────────────────────
    // 内部实现
    // ──────────────────────────────────────────────

    /**
     * 创建 Bean 实例并执行注入和初始化。
     */
    private Object createBeanInstance(BeanDefinition def) {
        Class<?> clazz = def.getBeanClass();
        LOG.debug("创建 Bean: {} ({})", def.getName(), clazz.getSimpleName());

        Object instance;

        // 优先使用 @Bean 工厂方法
        if (def.getFactoryMethod() != null) {
            instance = invokeFactoryMethod(def.getFactoryMethod(), def.getFactoryBeanInstance());
        } else {
            // 构造器注入
            instance = createWithConstructor(clazz);
            // 字段注入
            injectFields(instance);
        }

        // 处理 @Value 字段注入
        injectValues(instance);

        // 执行 @PostConstruct
        invokePostConstruct(instance);

        return instance;
    }

    /**
     * 通过构造器创建实例（支持 @Inject 构造器）。
     */
    private Object createWithConstructor(Class<?> clazz) {
        // 查找 @Inject 标注的构造器
        Constructor<?> injectedCtor = null;
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (ctor.isAnnotationPresent(Inject.class)) {
                if (injectedCtor != null) {
                    throw new RuntimeException("类 " + clazz.getSimpleName() + " 有多个 @Inject 构造器");
                }
                injectedCtor = ctor;
            }
        }

        try {
            if (injectedCtor != null) {
                // 带 @Inject 的构造器注入
                injectedCtor.setAccessible(true);
                Class<?>[] paramTypes = injectedCtor.getParameterTypes();
                Annotation[][] paramAnns = injectedCtor.getParameterAnnotations();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    String value = findValueAnnotation(paramAnns[i]);
                    if (value != null) {
                        args[i] = resolvePlaceholder(value, paramTypes[i]);
                    } else {
                        args[i] = getBean(paramTypes[i]);
                    }
                }
                return injectedCtor.newInstance(args);
            } else {
                // 无参构造器
                Constructor<?> noArgCtor = clazz.getDeclaredConstructor();
                noArgCtor.setAccessible(true);
                return noArgCtor.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("创建 Bean 实例失败: " + clazz.getSimpleName(), e);
        }
    }

    /**
     * 对实例执行字段注入（@Inject 标注的字段）。
     */
    private void injectFields(Object instance) {
        Class<?> clazz = instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                field.setAccessible(true);
                try {
                    Object dependency = getBean(field.getType());
                    field.set(instance, dependency);
                    LOG.debug("字段注入: {}.{} <- {}", clazz.getSimpleName(), field.getName(),
                        dependency.getClass().getSimpleName());
                } catch (Exception e) {
                    throw new RuntimeException("注入字段失败: " + clazz.getSimpleName() + "." + field.getName(), e);
                }
            }
        }
        // 方法注入（setter 注入）
        injectMethods(instance);
    }

    /**
     * 对实例执行 setter 注入（@Inject 标注的方法）。
     */
    private void injectMethods(Object instance) {
        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Inject.class)) {
                int paramCount = method.getParameterCount();
                if (paramCount == 0) continue;
                method.setAccessible(true);
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] args = new Object[paramCount];
                boolean allResolved = true;
                for (int i = 0; i < paramCount; i++) {
                    try {
                        args[i] = getBean(paramTypes[i]);
                    } catch (Exception e) {
                        allResolved = false;
                        break;
                    }
                }
                if (allResolved) {
                    try {
                        method.invoke(instance, args);
                        LOG.debug("方法注入: {}.{}({})", clazz.getSimpleName(), method.getName(),
                            args.length > 0 ? args[0].getClass().getSimpleName() : "");
                    } catch (Exception e) {
                        LOG.warn("方法注入失败: {}.{}: {}", clazz.getSimpleName(), method.getName(), e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 处理 @Value 字段。
     */
    private void injectValues(Object instance) {
        Class<?> clazz = instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            Value valueAnn = field.getAnnotation(Value.class);
            if (valueAnn == null) continue;
            field.setAccessible(true);
            try {
                Object resolved = resolvePlaceholder(valueAnn.value(), field.getType());
                field.set(instance, resolved);
                LOG.debug("@Value 注入: {}.{} = {}", clazz.getSimpleName(), field.getName(), resolved);
            } catch (Exception e) {
                LOG.warn("注入 @Value 失败: {}.{}: {}", clazz.getSimpleName(), field.getName(), e.getMessage());
            }
        }
    }

    /**
     * 执行 @PostConstruct 方法。
     */
    private void invokePostConstruct(Object instance) {
        Class<?> clazz = instance.getClass();
        String methodName = findPostConstruct(clazz);
        if (methodName == null) return;
        try {
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(instance);
            LOG.debug("执行 @PostConstruct: {}.{}()", clazz.getSimpleName(), methodName);
        } catch (Exception e) {
            LOG.warn("执行 @PostConstruct 失败: {}.{}: {}", clazz.getSimpleName(), methodName, e.getMessage());
        }
    }

    /**
     * 从注解数组中查找 @Value 注解。
     */
    private String findValueAnnotation(Annotation[] annotations) {
        for (Annotation ann : annotations) {
            if (ann instanceof Value) {
                return ((Value) ann).value();
            }
        }
        return null;
    }

    /**
     * 解析 "${key:default}" 占位符。
     */
    Object resolvePlaceholder(String expression, Class<?> targetType) {
        Pattern pattern = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
        Matcher matcher = pattern.matcher(expression);

        if (matcher.matches()) {
            String key = matcher.group(1);
            String defaultValue = matcher.group(2);
            String value = propertySource.getOrDefault(key, defaultValue);
            return convertValue(value, targetType, expression);
        }

        return convertValue(expression, targetType, expression);
    }

    private Object convertValue(String value, Class<?> targetType, String expression) {
        if (value == null) {
            throw new RuntimeException("无法解析 @Value: " + expression);
        }
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value);
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        return value;
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    /**
     * 从 @Component 注解确定 Bean 名称。
     */
    static String resolveBeanName(Class<?> clazz, Component component) {
        if (!component.name().isEmpty()) return component.name();
        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 查找类中带有 @PostConstruct 注解的方法名。
     */
    static String findPostConstruct(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                return method.getName();
            }
        }
        return null;
    }

    public boolean isRefreshed() { return refreshed; }
}
