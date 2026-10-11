package com.hbjt.fitness.agent.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 会话库连接配置。
 *
 * <p>两个刻意的选择：
 * <ol>
 *   <li><b>连接池不"快速失败"</b>：{@code initializationFailTimeout = -1}
 *       让数据库没起来时应用照常启动，把可用性判断留给业务调用点。
 *       一个可选能力（历史记录）不该让整个智能体起不来。</li>
 *   <li><b>自建 DataSource 并排除自动配置</b>（见启动类 exclude）：
 *       避免 Spring Boot 在没有 {@code spring.datasource.*} 时尝试猜驱动而报错，
 *       也让"这个连接池只服务于会话存储"这件事在代码里一目了然。</li>
 * </ol>
 */
@Configuration
@EnableTransactionManagement
public class ChatDbConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource chatDataSource(DbProperties props) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("fitness-chat");
        cfg.setJdbcUrl(props.getUrl());
        cfg.setUsername(props.getUsername());
        cfg.setPassword(props.getPassword());
        cfg.setMaximumPoolSize(6);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(props.getConnectTimeoutMs());
        cfg.setValidationTimeout(Math.min(2000, Math.max(1000, props.getConnectTimeoutMs())));
        // 启动时不去连库：连不上就延迟到首次使用时再试，不影响启动
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    @Bean
    public JdbcTemplate chatJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager chatTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 显式事务模板，供 {@link ChatStore} 在方法体内手动开启事务。
     *
     * <p>为什么不用 {@code @Transactional}：注解式事务由代理在<b>方法体外</b>开启——
     * 连接是在进入方法前取的，一旦数据库不可用，{@code CannotGetJdbcConnectionException}
     * 会在方法体的 try/catch 之外抛出，直接冒泡成 500。而本模块的硬性约定是
     * "存储不可用只降级、不阻断对话"，所以必须把取连接这件事挪进方法体里，
     * 让失败能被同一段 catch 兜住。
     */
    @Bean
    public TransactionTemplate chatTransactionTemplate(PlatformTransactionManager chatTransactionManager) {
        return new TransactionTemplate(chatTransactionManager);
    }
}
