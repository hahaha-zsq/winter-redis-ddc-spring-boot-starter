package com.zsq.winter.redis.ddc.util;

import org.springframework.core.io.ClassPathResource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lua脚本加载器工具类
 * 用于从classpath加载Lua脚本文件，并提供缓存机制提高性能
 */
public final class LuaScriptLoader {

    /**
     * Lua脚本内容缓存Map
     * Key: classpath资源路径
     * Value: 脚本内容字符串
     */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，防止实例化
     * 该类只提供静态方法，无需实例化
     */
    private LuaScriptLoader() {
    }

    /**
     * 加载指定classpath路径下的Lua脚本内容
     * 如果缓存中存在则直接返回，否则从文件系统加载并缓存
     *
     * @param classpathResource classpath下的资源路径，例如："lua/fixed_window.lua"
     * @return Lua脚本文件的内容字符串
     * @throws RuntimeException 当脚本加载失败时抛出运行时异常
     */
    public static String load(String classpathResource) {
        // 使用ConcurrentHashMap的computeIfAbsent方法实现缓存机制
        // 如果缓存中存在该资源路径，则直接返回缓存内容
        // 否则执行Lambda表达式加载资源并放入缓存
        return CACHE.computeIfAbsent(classpathResource, path -> {
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                // 使用ByteArrayOutputStream手动读取输入流
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = in.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                // 加载失败时抛出运行时异常，包含资源路径和原始异常信息
                throw new RuntimeException("Load lua script failed: " + path, e);
            }
        });
    }
}
