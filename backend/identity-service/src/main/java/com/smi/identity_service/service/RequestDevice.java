package com.smi.identity_service.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The browser and address behind the current request. The web app's server and
 * api-gateway pass the customer's own User-Agent and X-Forwarded-For along, so
 * this names the customer's device, not the server in between.
 */
public record RequestDevice(String userAgent, String ipAddress) {

    public static RequestDevice current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return new RequestDevice(null, null);
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        String agent = request.getHeader("User-Agent");
        return new RequestDevice(agent == null ? null : agent.substring(0, Math.min(agent.length(), 1000)), ip);
    }

    /** "Chrome on Windows", "Safari on iPhone", "Edge on Windows"... from a User-Agent header. */
    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent;
        String browser;
        if (ua.contains("Edg/") || ua.contains("Edge/")) browser = "Edge";
        else if (ua.contains("OPR/") || ua.contains("Opera")) browser = "Opera";
        else if (ua.contains("SamsungBrowser")) browser = "Samsung Internet";
        else if (ua.contains("Firefox/") || ua.contains("FxiOS")) browser = "Firefox";
        else if (ua.contains("Chrome/") || ua.contains("CriOS")) browser = "Chrome";
        else if (ua.contains("Safari/")) browser = "Safari";
        else if (ua.startsWith("okhttp") || ua.contains("Dalvik")) browser = "SmartMoney app";
        else if (ua.startsWith("node") || ua.startsWith("Java") || ua.startsWith("curl") || ua.startsWith("Bruno")) browser = "API client";
        else browser = "Browser";

        String os;
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("iPhone")) os = "iPhone";
        else if (ua.contains("iPad")) os = "iPad";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) os = "macOS";
        else if (ua.contains("CrOS")) os = "ChromeOS";
        else if (ua.contains("Linux")) os = "Linux";
        else os = null;
        return os == null ? browser : browser + " on " + os;
    }
}
