package com.smi.assistant_service.model;

import java.util.HashMap;
import java.util.Map;

public class ScreenContext {

    private String route;
    private String title;
    private Map<String, Object> data = new HashMap<>();

    public ScreenContext() {
    }

    public ScreenContext(String route, String title, Map<String, Object> data) {
        this.route = route;
        this.title = title;
        this.data = data != null ? data : new HashMap<>();
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new HashMap<>();
    }

    public String getFormattedSummary() {
        if (data == null || data.isEmpty()) {
            return "Screen: " + (title != null ? title : route);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title != null ? title : route).append(" details: ");
        data.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
        return sb.toString();
    }
}
