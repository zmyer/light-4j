package com.networknt.handler;

import com.networknt.config.Config;
import com.networknt.handler.config.HandlerConfig;
import com.networknt.handler.config.HandlerPath;
import com.networknt.service.ServiceUtil;
import com.networknt.utility.ModuleRegistry;
import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PathMiddlewareHandler implements NonFunctionalMiddlewareHandler {

    private static final String CONFIG_NAME = "handler";
    public static final HandlerConfig config = (HandlerConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, HandlerConfig.class);
    private volatile HttpHandler next;
    private String handlerName;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        // Doesn't get called.
    }

    private List<HandlerPath> getHandlerPaths() {
        return config.getPathHandlers().stream()
                .filter(pathHandler -> pathHandler.getHandlerName().equals(handlerName))
                .filter(pathHandler -> pathHandler.getPaths() != null && pathHandler.getPaths().size() > 0)
                .flatMap(pathHandler -> pathHandler.getPaths().stream())
                .collect(Collectors.toList());
    }

    private HttpHandler getHandler(HandlerPath handlerPath) {
        HttpHandler httpHandler = null;
        try {
            Object object = ServiceUtil.construct(handlerPath.getEndPoint());
            if (object instanceof HttpHandler) {
                httpHandler = (HttpHandler) object;
                List<Object> updatedList = new ArrayList<>(handlerPath.getMiddleware());
                Collections.reverse(updatedList);
                for (Object middleware : updatedList) {
                    Object constructedMiddleware = ServiceUtil.construct(middleware);
                    if (constructedMiddleware instanceof MiddlewareHandler) {
                        MiddlewareHandler middlewareHandler = (MiddlewareHandler) constructedMiddleware;
                        if (middlewareHandler.isEnabled()) {
                            httpHandler = middlewareHandler.setNext(httpHandler);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed when retrieving Handler.", e);
        }
        return httpHandler;
    }

    public PathMiddlewareHandler() {}

    @Override
    public HttpHandler getNext() {
        return next;
    }

    @Override
    public MiddlewareHandler setNext(final HttpHandler next) {
        Handlers.handlerNotNull(next);
        RoutingHandler routingHandler = Handlers.routing().setFallbackHandler(next);
        for (HandlerPath handlerPath : getHandlerPaths()) {
            try {
                HttpHandler httpHandler = getHandler(handlerPath);
                if (httpHandler != null) {
                    routingHandler.add(handlerPath.getHttpVerb(), handlerPath.getPath(), httpHandler);
                }
            } catch (Exception e) {
                logger.error("Failed to add PathMiddlewareHandler.", e);
            }
        }
        this.next = routingHandler;
        return this;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(HandlerConfig.class.getName(), Config.getInstance().getJsonMapConfigNoCache(CONFIG_NAME), null);
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }
}
