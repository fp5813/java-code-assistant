package com.moma.di;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DI 容器核心功能测试。
 * 验证 @Component/@Inject/@Configuration/@Bean/@Value/@PostConstruct/@Primary 等注解。
 */
class ApplicationContextTest {

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ApplicationContext();
    }

    // ─────────────────────────────────────────────
    // 测试组件
    // ─────────────────────────────────────────────

    @Component(name = "helloService")
    static class HelloService {
        String greet() { return "Hello, DI!"; }
    }

    @Component
    static class GreetingService {
        private HelloService helloService;

        @Inject
        public void setHelloService(HelloService helloService) {
            this.helloService = helloService;
        }

        String greet() { return helloService.greet(); }

        HelloService getHelloService() { return helloService; }
    }

    @Component(scope = "prototype")
    static class PrototypeBean {
        private static int instanceCount = 0;
        final int id;

        PrototypeBean() {
            this.id = ++instanceCount;
        }

        static void reset() { instanceCount = 0; }
    }

    @Component
    @Primary
    static class PrimaryBean implements Greeter {
        @Override
        public String greet() { return "Primary"; }
    }

    @Component
    static class SecondaryBean implements Greeter {
        @Override
        public String greet() { return "Secondary"; }
    }

    interface Greeter {
        String greet();
    }

    @Component
    static class PostConstructBean {
        boolean initialized = false;

        @PostConstruct
        void init() {
            initialized = true;
        }
    }

    @Component
    static class CircularA {
        @Inject
        private CircularB b;
    }

    @Component
    static class CircularB {
        @Inject
        private CircularA a;
    }

    @Configuration
    static class TestConfig {

        @Bean(name = "configGreeter")
        public Greeter configGreeter() {
            return () -> "From @Bean method";
        }

        @Bean
        @Primary
        public Greeter primaryGreeter() {
            return () -> "@Primary @Bean";
        }
    }

    @Component
    static class ValueInjectedBean {
        @Value("${test.host:default-host}")
        String host;
        @Value("${test.port:8080}")
        int port;
        @Value("${test.debug:false}")
        boolean debug;
    }

    @Component
    static class ConstructorInjectedBean {
        final HelloService helloService;

        @Inject
        public ConstructorInjectedBean(HelloService helloService) {
            this.helloService = helloService;
        }
    }

    // ─────────────────────────────────────────────
    // 测试用例
    // ─────────────────────────────────────────────

    @Test
    void testRegisterAndGetBean() {
        ctx.register(HelloService.class);
        ctx.refresh();

        HelloService service = ctx.getBean(HelloService.class);
        assertNotNull(service);
        assertEquals("Hello, DI!", service.greet());
    }

    @Test
    void testBeanByName() {
        ctx.register(HelloService.class);
        ctx.refresh();

        HelloService service = ctx.getBean("helloService");
        assertNotNull(service);
    }

    @Test
    void testFieldInjection() {
        ctx.register(HelloService.class);
        ctx.register(GreetingService.class);
        ctx.refresh();

        GreetingService greeting = ctx.getBean(GreetingService.class);
        assertNotNull(greeting);
        assertNotNull(greeting.getHelloService());
        assertEquals("Hello, DI!", greeting.greet());
    }

    @Test
    void testSingletonScope() {
        ctx.register(HelloService.class);
        ctx.refresh();

        HelloService s1 = ctx.getBean("helloService");
        HelloService s2 = ctx.getBean("helloService");
        assertSame(s1, s2, "单例 Bean 应返回相同实例");
    }

    @Test
    void testPrototypeScope() {
        PrototypeBean.reset();
        ctx.register(PrototypeBean.class);
        ctx.refresh();

        PrototypeBean p1 = ctx.getBean(PrototypeBean.class);
        PrototypeBean p2 = ctx.getBean(PrototypeBean.class);
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotSame(p1, p2, "原型 Bean 应返回不同实例");
    }

    @Test
    void testPrimaryAnnotation() {
        ctx.register(SecondaryBean.class);
        ctx.register(PrimaryBean.class);
        ctx.refresh();

        Greeter greeter = ctx.getBean(Greeter.class);
        assertNotNull(greeter);
        assertEquals("Primary", greeter.greet(), "@Primary Bean 应优先注入");
    }

    @Test
    void testPostConstruct() {
        ctx.register(PostConstructBean.class);
        ctx.refresh();

        PostConstructBean bean = ctx.getBean(PostConstructBean.class);
        assertTrue(bean.initialized, "@PostConstruct 方法应被执行");
    }

    @Test
    void testCircularDependency() {
        ctx.register(CircularA.class);
        ctx.register(CircularB.class);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> ctx.refresh());
        // 可能直接抛出循环依赖异常，也可能包装在"注入字段失败"中
        boolean directMatch = ex.getMessage().contains("循环依赖");
        boolean causeMatch = ex.getCause() instanceof RuntimeException
            && ex.getCause().getMessage().contains("循环依赖");
        boolean fieldInjectMatch = ex.getMessage().contains("注入字段失败");
        assertTrue(directMatch || causeMatch || fieldInjectMatch,
            "应检测到循环依赖: " + ex.getMessage());
    }

    @Test
    void testConfigurationAndBean() {
        ctx.register(HelloService.class);
        ctx.register(TestConfig.class);
        ctx.refresh();

        Greeter greeter = ctx.getBean("configGreeter");
        assertNotNull(greeter);
        assertEquals("From @Bean method", greeter.greet());
    }

    @Test
    void testPrimaryBeanMethod() {
        ctx.register(TestConfig.class);
        ctx.refresh();

        Greeter greeter = ctx.getBean(Greeter.class);
        assertNotNull(greeter);
        assertEquals("@Primary @Bean", greeter.greet());
    }

    @Test
    void testValueAnnotation() {
        Map<String, String> props = new HashMap<>();
        props.put("test.host", "myhost");
        props.put("test.port", "9090");
        props.put("test.debug", "true");
        ctx.setPropertySource(props);
        ctx.register(ValueInjectedBean.class);
        ctx.refresh();

        ValueInjectedBean bean = ctx.getBean(ValueInjectedBean.class);
        assertEquals("myhost", bean.host);
        assertEquals(9090, bean.port);
        assertTrue(bean.debug);
    }

    @Test
    void testValueDefault() {
        ctx.register(ValueInjectedBean.class);
        ctx.refresh();

        ValueInjectedBean bean = ctx.getBean(ValueInjectedBean.class);
        assertEquals("default-host", bean.host);
        assertEquals(8080, bean.port);
        assertFalse(bean.debug);
    }

    @Test
    void testConstructorInjection() {
        ctx.register(HelloService.class);
        ctx.register(ConstructorInjectedBean.class);
        ctx.refresh();

        ConstructorInjectedBean bean = ctx.getBean(ConstructorInjectedBean.class);
        assertNotNull(bean.helloService);
        assertEquals("Hello, DI!", bean.helloService.greet());
    }

    @Test
    void testGetBeanByTypeNotFound() {
        ctx.refresh();
        assertThrows(RuntimeException.class, () -> ctx.getBean(HelloService.class));
    }

    @Test
    void testContainsBean() {
        ctx.register(HelloService.class);
        ctx.refresh();
        assertTrue(ctx.containsBean("helloService"));
        assertFalse(ctx.containsBean("nonExistent"));
    }

    @Test
    void testGetBeanNames() {
        ctx.register(HelloService.class);
        ctx.register(TestConfig.class);
        ctx.refresh();
        assertTrue(ctx.getBeanNames().contains("helloService"));
    }

    @Test
    void testRefreshFlag() {
        assertFalse(ctx.isRefreshed());
        ctx.register(HelloService.class);
        ctx.refresh();
        assertTrue(ctx.isRefreshed());
    }

    @Test
    void testResolvePlaceholder() {
        Map<String, String> props = new HashMap<>();
        props.put("my.key", "myvalue");
        ctx.setPropertySource(props);

        Object result = ctx.resolvePlaceholder("${my.key:default}", String.class);
        assertEquals("myvalue", result);
    }

    @Test
    void testResolvePlaceholderDefault() {
        Object result = ctx.resolvePlaceholder("${undefined:fallback}", String.class);
        assertEquals("fallback", result);
    }

    @Test
    void testResolvePlaceholderInt() {
        Map<String, String> props = new HashMap<>();
        props.put("num", "42");
        ctx.setPropertySource(props);

        Object result = ctx.resolvePlaceholder("${num:0}", int.class);
        assertEquals(42, result);
    }

    @Test
    void testFindPostConstruct() {
        assertNotNull(ApplicationContext.findPostConstruct(PostConstructBean.class));
        assertNull(ApplicationContext.findPostConstruct(HelloService.class));
    }

    @Test
    void testResolveBeanName() {
        Component comp = HelloService.class.getAnnotation(Component.class);
        String name = ApplicationContext.resolveBeanName(HelloService.class, comp);
        assertEquals("helloService", name);
    }
}
