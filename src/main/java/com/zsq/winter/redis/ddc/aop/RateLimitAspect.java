package com.zsq.winter.redis.ddc.aop;

import com.zsq.winter.redis.ddc.annotation.RateLimit;
import com.zsq.winter.redis.ddc.enums.LimitAlgorithm;
import com.zsq.winter.redis.ddc.expection.RateLimitException;
import com.zsq.winter.redis.ddc.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面类，基于Spring AOP实现方法级别的限流控制
 * 支持多种限流算法，通过注解方式配置限流规则
 */
@Slf4j
@Aspect
public class RateLimitAspect {

    /**
     * 限流服务接口，用于执行具体的限流逻辑
     */
    private final RateLimiterService rateLimiterService;

    /**
     * Spring表达式解析器，用于解析SpEL表达式
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * SpEL表达式缓存，提高表达式解析性能
     * Key: 类名#方法名:表达式
     * Value: 解析后的表达式对象
     */
    private final Map<String, Expression> spelCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param rateLimiterService 限流服务实现类
     */
    public RateLimitAspect(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * 环绕通知方法，处理限流逻辑
     * 拦截所有带有@RateLimit注解的方法
     *
     * @param pjp       连接点对象，包含被拦截方法的信息
     * @param rateLimit 限流注解实例，包含限流配置信息
     * @return 被拦截方法的执行结果
     * @throws Throwable 方法执行异常或限流异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        // 解析限流键名表达式，获取实际的限流键值
        String keySpel = rateLimit.key();
        String key = parseKey(keySpel, pjp);

        // 获取限流配置参数
        LimitAlgorithm alg = rateLimit.algorithm();        // 限流算法类型
        double permits = rateLimit.permitsPerSecond();     // 每秒允许的请求数
        long window = rateLimit.windowSize();              // 时间窗口大小
        long capacity = rateLimit.capacity();              // 令牌桶容量/漏桶容量

        // 执行限流检查
        boolean allowed = rateLimiterService.tryAcquire(key, alg, permits, window, capacity);
        if (!allowed) {
            log.warn("Rate limit exceeded: key={}, algorithm={}, permits={}, window={}, capacity={}",
                    key, alg, permits, window, capacity);
            // 限流检查不通过，抛出限流异常
            throw new RateLimitException("Rate limit exceeded for key: " + key);
        }

        // 限流检查通过，继续执行原方法
        return pjp.proceed();
    }

    /**
     * 解析SpEL表达式键名，生成实际的限流键值
     * 支持使用方法参数动态生成限流键
     *
     * @param spel SpEL表达式字符串
     * @param pjp  连接点对象，用于获取方法参数
     * @return 解析后的键值字符串
     */
    private String parseKey(String spel, ProceedingJoinPoint pjp) {
        if (spel == null || spel.trim().isEmpty()) {
            return defaultKey(pjp);
        }

        boolean hasVar = spel.contains("#");
        if (!hasVar) {
            return spel;
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();

        String cacheKey = method.getDeclaringClass().getName() + "#" + method.getName() + ":" + spel;
        Expression expression = spelCache.computeIfAbsent(cacheKey, k -> parser.parseExpression(spel));

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setRootObject(pjp.getTarget());
        ctx.setVariable("target", pjp.getTarget());
        ctx.setVariable("method", method);

        Object[] args = pjp.getArgs();
        if (args != null) {
            ctx.setVariable("args", args);
            for (int i = 0; i < args.length; i++) {
                ctx.setVariable("p" + i, args[i]);
                ctx.setVariable("a" + i, args[i]);
            }
        }

        String[] paramNames = signature.getParameterNames();
        if (paramNames != null && args != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Object value = expression.getValue(ctx, Object.class);
            String resolved = value == null ? null : value.toString();
            return (resolved == null || resolved.trim().isEmpty()) ? defaultKey(pjp) : resolved;
        } catch (Exception e) {
            return defaultKey(pjp);
        }
    }

    private String defaultKey(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }
}
