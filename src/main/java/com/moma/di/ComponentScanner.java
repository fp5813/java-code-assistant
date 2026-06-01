package com.moma.di;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 组件扫描器。扫描指定包路径下的 {@link Component} 和 {@link Configuration} 类。
 *
 * <p>支持从文件系统目录和 JAR 文件中扫描。</p>
 */
public class ComponentScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ComponentScanner.class);

    /**
     * 扫描指定基础包，返回所有标记了 {@link Component} 或 {@link Configuration} 的类。
     *
     * @param basePackages 要扫描的基础包名列表
     * @return 扫描到的组件类集合
     */
    public Set<Class<?>> scan(String... basePackages) {
        Set<Class<?>> components = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            try {
                scanPackage(basePackage, components);
            } catch (Exception e) {
                LOG.warn("扫描包 {} 失败: {}", basePackage, e.getMessage());
            }
        }
        LOG.info("组件扫描完成: 发现 {} 个候选类", components.size());
        return components;
    }

    private void scanPackage(String basePackage, Set<Class<?>> components) throws Exception {
        String packagePath = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        Enumeration<URL> resources = classLoader.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                File file = new File(filePath);
                if (file.isDirectory()) {
                    scanDirectory(file, basePackage, components);
                }
            } else if ("jar".equals(protocol)) {
                scanJar(resource, basePackage, components);
            }
        }
    }

    private void scanDirectory(File dir, String packageName, Set<Class<?>> components) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), components);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                registerIfComponent(className, components);
            }
        }
    }

    private void scanJar(URL jarUrl, String basePackage, Set<Class<?>> components) {
        String jarPath = jarUrl.getPath();
        // 解析 JAR 路径：可能格式为 "file:/path/to/jar!/package/path"
        int separatorIndex = jarPath.indexOf("!");
        if (separatorIndex < 0) return;

        String jarFilePath = jarPath.substring(0, separatorIndex);
        if (jarFilePath.startsWith("file:")) {
            jarFilePath = jarFilePath.substring(5);
        }

        String packagePath = basePackage.replace('.', '/');

        try (JarFile jarFile = new JarFile(URLDecoder.decode(jarFilePath, StandardCharsets.UTF_8))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName.replace('/', '.').replace(".class", "");
                    registerIfComponent(className, components);
                }
            }
        } catch (IOException e) {
            LOG.warn("扫描 JAR 失败: {}", e.getMessage());
        }
    }

    private void registerIfComponent(String className, Set<Class<?>> components) {
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum()) return;
            if (clazz.isAnnotationPresent(Component.class) || clazz.isAnnotationPresent(Configuration.class)) {
                components.add(clazz);
                LOG.debug("扫描到组件: {}", className);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            LOG.debug("跳过类 {}: {}", className, e.getMessage());
        }
    }
}
