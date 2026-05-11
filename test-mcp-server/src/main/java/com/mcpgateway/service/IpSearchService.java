package com.mcpgateway.service;

import com.mcpgateway.domain.IpQueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Service
public class IpSearchService {

    private static final Logger logger = LoggerFactory.getLogger(IpSearchService.class);

    private final RestClient restClient;

    public IpSearchService() {
        this.restClient = RestClient.builder()
                .build();
    }


    @Tool(name = "getLocationByIp", description = "根据ip获取归属地")
    public String getLocationByIp(@ToolParam(description = "ip地址, 使用ipv4的格式") String ip) {
        if (Objects.isNull(ip)) {
            return "错误：ip地址不能为空";
        }
        String location = getLocation(ip);
        if (Objects.isNull(location)) {
            return "错误：未查找到ip的归属地";
        } else {
            return "ip归属地：" + location;
        }
    }


    public String getLocation(String ip) {
        try {
            var ipInfoDto = restClient.get()
                    .uri("https://opendata.baidu.com/api.php?query={ip}&co=&resource_id=6006&oe=utf8", ip)
                    .retrieve()
                    .body(IpQueryResponse.class);
            if (ipInfoDto != null && ipInfoDto.getData() != null && ipInfoDto.getData().length > 0) {
                return ipInfoDto.getData()[0].getLocation();
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("获取ip位置信息失败", e);
            return null;
        }
    }
}
