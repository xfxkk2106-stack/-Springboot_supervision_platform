package com.supervision;

import com.supervision.mapper.RoomMemberMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.supervision.mapper")
public class SupervisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupervisionApplication.class, args);
    }

    @Bean
    public CommandLineRunner resetOnlineStatus(RoomMemberMapper roomMemberMapper) {
        return args -> {
            try {
                roomMemberMapper.resetAllOffline();
            } catch (Exception e) {
                // 忽略
            }
        };
    }
}
