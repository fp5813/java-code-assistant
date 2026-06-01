package com.moma.service;

import com.moma.agent.SessionManager;
import com.moma.di.Component;
import com.moma.di.Inject;

import java.util.List;

/**
 * 会话服务层。封装会话管理操作。
 */
@Component
public class SessionService {

    private final SessionManager sessionManager;

    @Inject
    public SessionService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 获取当前会话 ID。
     */
    public String getCurrentSessionId() {
        return sessionManager.getSessionId();
    }

    /**
     * 列出最近会话。
     */
    public List<SessionManager.SessionSummary> listRecentSessions(int limit) {
        var all = SessionManager.listSessions();
        return all.subList(0, Math.min(all.size(), limit));
    }
}
