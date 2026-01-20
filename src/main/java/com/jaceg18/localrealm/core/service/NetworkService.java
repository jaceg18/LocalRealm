package com.jaceg18.localrealm.core.service;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkService {
    
    private static final int RANDOM_PORT_MIN = 30000;
    private static final int RANDOM_PORT_MAX = 45000;
    private static final String[] PUBLIC_IP_SERVICES = {
        "https://api.ipify.org",
        "https://icanhazip.com",
        "https://checkip.amazonaws.com"
    };
    
    private static final Pattern UPNP_IGD_LOCATION = Pattern.compile("location:\\s*(http://[^\\r\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPNP_SERVICE_TYPE = Pattern.compile("urn:schemas-upnp-org:service:WANIPConnection:1|urn:schemas-upnp-org:service:WANPPPConnection:1");

    public record NetworkConfig(String localLanIp, int internalPort, int externalPort, String publicIp,
                                String igdControlUrl, String igdServiceType, boolean mappingActive) {

        public String getJoinAddress() {
                return publicIp + ":" + externalPort;
            }
        }

    public static String findLocalLanIp() throws IOException {
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new IOException("Failed to enumerate network interfaces", e);
        }
        
        try {
            NetworkInterface defaultRoute = null;

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("8.8.8.8", 53), 1000);
                NetworkInterface iface = NetworkInterface.getByInetAddress(socket.getLocalAddress());
                if (iface != null) {
                    defaultRoute = iface;
                }
            } catch (IOException e) {
                // Fall through to enumeration method
            }

            if (defaultRoute == null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (!iface.isUp() || iface.isLoopback()) continue;
                    
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && hasDefaultRoute(iface)) return addr.getHostAddress();
                    }
                }
            } else {
                Enumeration<InetAddress> addresses = defaultRoute.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            
            // Fallback: first non-loopback IPv4
            try {
                interfaces = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                throw new IOException("Failed to enumerate network interfaces for fallback", e);
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            
            throw new IOException("No suitable network interface found");
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Unexpected error finding network interface", e);
        }
    }
    
    private static boolean hasDefaultRoute(NetworkInterface iface) {
        try {
            return iface.isUp() && !iface.isLoopback();
        } catch (SocketException e) {
            return false; // If we can't check, assume no default route
        }
    }
    
    /**
     * Discovers UPnP IGD device and returns control URL.
     */
    public static Map<String, String> discoverUpnpIgd() throws IOException {
        try {
            InetAddress multicastAddr = InetAddress.getByName("239.255.255.250");
            final int multicastPort = 1900;
            String searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
                    "MX: 3\r\n\r\n";
            
            DatagramSocket socket;
            try {
                socket = new DatagramSocket();
            } catch (SocketException e) {
                throw new IOException("Failed to create UDP socket for UPnP discovery", e);
            }
            socket.setSoTimeout(3000);
            
            byte[] requestData = searchMessage.getBytes(StandardCharsets.UTF_8);
            DatagramPacket request = new DatagramPacket(
                    requestData, requestData.length, multicastAddr, multicastPort);
            
            socket.send(request);
            
            byte[] buffer = new byte[2048];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            
            try {
                socket.receive(response);
                String responseText = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
                
                Matcher locationMatcher = UPNP_IGD_LOCATION.matcher(responseText);
                if (locationMatcher.find()) {
                    String location = locationMatcher.group(1);

                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                    
                    HttpRequest descRequest = HttpRequest.newBuilder()
                            .uri(URI.create(location))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    
                    HttpResponse<String> descResponse = client.send(descRequest, HttpResponse.BodyHandlers.ofString());
                    String descXml = descResponse.body();

                    String serviceType = extractServiceType(descXml);
                    String controlUrl = extractControlUrl(descXml, serviceType);
                    
                    if (serviceType != null && controlUrl != null) {
                        // Make controlUrl absolute
                        URI baseUri = URI.create(location);
                        if (!controlUrl.startsWith("http://") && !controlUrl.startsWith("https://")) {
                            controlUrl = baseUri.resolve(controlUrl).toString();
                        }
                        
                        Map<String, String> result = new HashMap<>();
                        result.put("controlUrl", controlUrl);
                        result.put("serviceType", serviceType);
                        result.put("location", location);
                        return result;
                    }
                }
            } catch (SocketTimeoutException e) {
                throw new IOException("UPnP discovery timeout - no IGD device found", e);
            } finally {
                socket.close();
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("UPnP discovery failed", e);
        }
        
        throw new IOException("No UPnP IGD device found");
    }
    
    private static String extractServiceType(String xml) {
        Matcher matcher = UPNP_SERVICE_TYPE.matcher(xml);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
    
    private static String extractControlUrl(String xml, String serviceType) {
        Pattern pattern = Pattern.compile(
                "<serviceType>" + Pattern.quote(serviceType) + "</serviceType>\\s*" +
                "<serviceId>.*?</serviceId>\\s*" +
                "<controlURL>(.*?)</controlURL>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Attempts to add a UPnP port mapping.
     * @return true if successful, false if port in use or mapping failed
     */
    public static boolean addPortMapping(String controlUrl, String serviceType, 
                                         String localIp, int internalPort, int externalPort) 
            throws IOException {
        String soapAction = "\"" + serviceType + "#AddPortMapping\"";
        String soapBody = "<?xml version=\"1.0\"?>\r\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                "<s:Body>\r\n" +
                "<u:AddPortMapping xmlns:u=\"" + serviceType + "\">\r\n" +
                "<NewRemoteHost></NewRemoteHost>\r\n" +
                "<NewExternalPort>" + externalPort + "</NewExternalPort>\r\n" +
                "<NewProtocol>TCP</NewProtocol>\r\n" +
                "<NewInternalPort>" + internalPort + "</NewInternalPort>\r\n" +
                "<NewInternalClient>" + localIp + "</NewInternalClient>\r\n" +
                "<NewEnabled>1</NewEnabled>\r\n" +
                "<NewPortMappingDescription>LocalRealm</NewPortMappingDescription>\r\n" +
                "<NewLeaseDuration>0</NewLeaseDuration>\r\n" +
                "</u:AddPortMapping>\r\n" +
                "</s:Body>\r\n" +
                "</s:Envelope>";
        
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(controlUrl))
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("SOAPAction", soapAction)
                    .POST(HttpRequest.BodyPublishers.ofString(soapBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check for UPnP error codes
            if (response.statusCode() == 200) {
                String body = response.body();
                // Check for error codes in response
                if (body.contains("errorCode")) {
                    Pattern errorPattern = Pattern.compile("<errorCode>(\\d+)</errorCode>");
                    Matcher errorMatcher = errorPattern.matcher(body);
                    if (errorMatcher.find()) {
                        int errorCode = Integer.parseInt(errorMatcher.group(1));
                        // 718 = ConflictInMappingEntry (port in use)
                        if (errorCode == 718) {
                            return false; // Port in use
                        }
                        // Other errors
                        throw new IOException("UPnP error code: " + errorCode);
                    }
                }
                return true;
            } else {
                throw new IOException("UPnP HTTP error: " + response.statusCode());
            }
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to add port mapping", e);
        }
    }
    
    /**
     * Removes a UPnP port mapping.
     */
    public static boolean removePortMapping(String controlUrl, String serviceType, int externalPort) {
        String soapAction = "\"" + serviceType + "#DeletePortMapping\"";
        String soapBody = "<?xml version=\"1.0\"?>\r\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                "<s:Body>\r\n" +
                "<u:DeletePortMapping xmlns:u=\"" + serviceType + "\">\r\n" +
                "<NewRemoteHost></NewRemoteHost>\r\n" +
                "<NewExternalPort>" + externalPort + "</NewExternalPort>\r\n" +
                "<NewProtocol>TCP</NewProtocol>\r\n" +
                "</u:DeletePortMapping>\r\n" +
                "</s:Body>\r\n" +
                "</s:Envelope>";
        
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(controlUrl))
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("SOAPAction", soapAction)
                    .POST(HttpRequest.BodyPublishers.ofString(soapBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false; // Best effort
        }
    }
    
    /**
     * Fetches public IPv4 address from external service.
     */
    public static String fetchPublicIp() throws IOException {
        for (String service : PUBLIC_IP_SERVICES) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(service))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    String ip = response.body().trim();
                    // Validate it's an IP
                    if (isValidIpv4(ip)) {
                        return ip;
                    }
                }
            } catch (Exception e) {
                // Try next service
            }
        }
        
        throw new IOException("Failed to fetch public IP from all services");
    }
    
    private static boolean isValidIpv4(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Attempts to verify port reachability (weak local test only).
     * Note: This is not authoritative but better than nothing.
     */
    public static boolean testPortReachability(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Tries to find an available external port for mapping.
     */
    public static int findAvailableExternalPort(int preferredPort) throws IOException {
        // Try preferred port first
        if (preferredPort > 0) {
            try (ServerSocket test = new ServerSocket(preferredPort)) {
                // Port is available locally, but UPnP might still reject it
                return preferredPort;
            } catch (IOException e) {
                // Port in use, try random
            }
        }
        
        // Try random ports in range
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            int port = RANDOM_PORT_MIN + random.nextInt(RANDOM_PORT_MAX - RANDOM_PORT_MIN);
            try (ServerSocket test = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                continue;
            }
        }
        
        throw new IOException("Could not find available port");
    }
}

