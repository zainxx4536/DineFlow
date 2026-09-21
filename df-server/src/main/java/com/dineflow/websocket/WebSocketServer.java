package com.dineflow.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 */
@Slf4j
@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    /**
     * 保存客户端标识与 WebSocket 会话
     * 可以这样理解：sid 是联系人 ID，Session 是当前保持着的聊天通道，
     * SESSION_MAP 是“在线联系人 → 聊天通道”的通讯录
     */
    private static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    /**
     * 建立连接
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {

        log.info("WebSocket客户端建立连接，sid={}", sid);

        // 如果同一个 sid 重复连接，新的连接会覆盖旧连接
        SESSION_MAP.put(sid, session);
    }

    /**
     * 接收客户端消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {

        log.info("收到WebSocket客户端消息，sid={}，message={}", sid, message);
    }

    /**
     * 关闭连接
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {

        SESSION_MAP.remove(sid);

        log.info("WebSocket连接断开，sid={}", sid);
    }

    /**
     * WebSocket异常
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {

        log.error("WebSocket连接异常，sid={}", sid, error);

        SESSION_MAP.remove(sid);
    }

    /**
     * 向所有客户端广播消息
     */
    public void sendToAllClient(String message) {

        SESSION_MAP.forEach((sid, session) -> {

            if (!session.isOpen()) {
                SESSION_MAP.remove(sid);
                return;
            }

            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("WebSocket消息发送失败，sid={}", sid, e);
            }
        });
    }

    /**
     * 向单个客户端发送消息
     */
    public void sendToClient(String sid, String message) {

        Session session = SESSION_MAP.get(sid);

        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("WebSocket消息发送失败，sid={}", sid, e);
        }
    }
}