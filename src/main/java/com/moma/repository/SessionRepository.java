package com.moma.repository;

import com.moma.agent.SessionManager;
import com.moma.di.Component;
import com.moma.di.Inject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话数据访问层。
 * 封装会话相关的持久化操作，提供会话查询接口。
 */
@Component
public class SessionRepository {

    private final SessionManager sessionManager;

    @Inject
    public SessionRepository(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 获取当前会话 ID。
     */
    public String getCurrentSessionId() {
        return sessionManager.getSessionId();
    }

    /**
     * 列出最近的会话列表。
     *
     * @param limit 返回数量上限
     * @return 会话摘要列表
     */
    public List<SessionManager.SessionSummary> listSessions(int limit) {
        return SessionManager.listSessions().stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * 获取底层 SessionManager 实例。
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }
}
