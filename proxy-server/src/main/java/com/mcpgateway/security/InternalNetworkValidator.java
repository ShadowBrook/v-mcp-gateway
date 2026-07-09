package com.mcpgateway.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public class InternalNetworkValidator {

    public boolean isInternal(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return true; // Block URLs without a host
            }
            // Resolve ALL IP addresses to mitigate DNS rebinding attacks
            // where a hostname resolves to different IPs across lookups
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isSiteLocalAddress()
                    || isPrivateIp(addr.getHostAddress())) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            return true; // Block unresolvable hosts
        }
    }

    private boolean isPrivateIp(String ip) {
        // Catch addresses not covered by the standard checks
        return ip.startsWith("0.")
            || ip.equals("255.255.255.255")
            || matchesCgnat(ip);
    }

    private boolean matchesCgnat(String ip) {
        // 100.64.0.0/10 — Carrier Grade NAT
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 100 && second >= 64 && second <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
