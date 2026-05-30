package com.supervision.task;

import com.supervision.service.TaskService;
import com.supervision.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.Cursor;
import java.util.Set;

@Component
public class UserCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(UserCleanupTask.class);
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    private static final String AUTH_TOKEN_PREFIX = "auth:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private TaskService taskService;

    /**
     * 每小时执行一次，清理无有效 token 的用户
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOrphanedUsers() {
        log.info("开始清理无有效 token 的用户...");

        int cleaned = 0;
        int scanned = 0;

        // SCAN 遍历所有 user_tokens:* 的 key
        ScanOptions options = ScanOptions.scanOptions().match(USER_TOKENS_PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String uid = key.substring(USER_TOKENS_PREFIX.length());
                scanned++;

                try {
                    // SSCAN 取一个 authToken（只读不移除）
                    Set<Object> tokens = redisTemplate.opsForSet().members(key);
                    if (tokens == null || tokens.isEmpty()) {
                        // Set 为空，清理
                        userService.cleanupUser(uid);
                        cleaned++;
                        continue;
                    }

                    // 取第一个 token 检查是否存在
                    String sampleToken = tokens.iterator().next().toString();
                    Boolean exists = redisTemplate.hasKey(AUTH_TOKEN_PREFIX + sampleToken);
                    if (exists == null || !exists) {
                        // token 不存在，清理该用户
                        userService.cleanupUser(uid);
                        cleaned++;
                    }
                } catch (Exception e) {
                    log.error("清理用户 {} 失败", uid, e);
                }
            }
        } catch (Exception e) {
            log.error("SCAN 遍历 Redis 失败", e);
        }

        log.info("清理完成：扫描 {} 个用户，清理 {} 个", scanned, cleaned);
    }

    /**
     * 每天 0 点执行，将明日计划转为今日任务
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void midnightConvertPlans() {
        log.info("开始凌晨转换明日计划为今日任务...");
        taskService.midnightConvertAllRooms();
        log.info("凌晨转换完成");
    }
}
