package com.mcpgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Service
public class WeatherQueryService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherQueryService.class);

    private static final String API_URL = "https://cn.apihz.cn/api/tianqi/tqyb.php";
    private static final String DEV_ID = "10016558";
    private static final String DEV_KEY = "4a0dce4c9668bd5e2ca5d456e782690f";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeatherQueryService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "getWeather", description = "查询指定地点1-7天天气预报，包含天气、温度、风力、降水、日出日落等信息")
    public String getWeather(
            @ToolParam(description = "省份名称，如：四川、北京、广东、云南") String sheng,
            @ToolParam(description = "地点名称，市级或区级名称，如：绵阳、大兴区、深圳、砚山") String place,
            @ToolParam(description = "查询天数，1到7，默认1") String day,
            @ToolParam(description = "是否返回各时段天气预报，0=不返回，1=返回，默认0") String hourtype,
            @ToolParam(description = "是否返回7天日出日落时间表，0=不返回，1=返回，默认0") String suntimetype) {

        if (Objects.isNull(place) || place.isBlank()) {
            return "错误：地点名称(place)不能为空";
        }

        try {
            StringBuilder uri = new StringBuilder(API_URL)
                    .append("?id=").append(DEV_ID)
                    .append("&key=").append(DEV_KEY)
                    .append("&place=").append(place);

            if (sheng != null && !sheng.isBlank()) {
                uri.append("&sheng=").append(sheng);
            }
            if (day != null && !day.isBlank()) {
                uri.append("&day=").append(day);
            }
            if (hourtype != null && !hourtype.isBlank()) {
                uri.append("&hourtype=").append(hourtype);
            }
            if (suntimetype != null && !suntimetype.isBlank()) {
                uri.append("&suntimetype=").append(suntimetype);
            }

            String response = restClient.get()
                    .uri(uri.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code != 200) {
                String msg = root.path("msg").asText("未知错误");
                return "天气查询失败：" + msg;
            }

            return formatWeatherResult(root);

        } catch (Exception e) {
            logger.warn("天气查询失败", e);
            return "天气查询失败：" + e.getMessage();
        }
    }

    private String formatWeatherResult(JsonNode root) {
        StringBuilder sb = new StringBuilder();

        String guo = root.path("guo").asText("");
        String sheng = root.path("sheng").asText("");
        String shi = root.path("shi").asText("");
        String uptime = root.path("uptime").asText("");

        sb.append("## ").append(guo).append(" ").append(sheng).append(" ").append(shi).append(" 天气\n");
        sb.append("更新时间：").append(uptime).append("\n\n");

        // 当前天气
        JsonNode nowinfo = root.path("nowinfo");
        if (!nowinfo.isMissingNode()) {
            sb.append("### 当前天气\n");
            sb.append("| 温度 | 体感温度 | 湿度 | 风向 | 风速 | 风级 | 降水 | 气压 |\n");
            sb.append("|------|----------|------|------|------|------|------|------|\n");
            sb.append("| ").append(nowinfo.path("temperature").asText("")).append("℃");
            sb.append(" | ").append(nowinfo.path("feelst").asText("")).append("℃");
            sb.append(" | ").append(nowinfo.path("humidity").asText("")).append("%");
            sb.append(" | ").append(nowinfo.path("windDirection").asText(""));
            sb.append(" | ").append(nowinfo.path("windSpeed").asText("")).append("m/s");
            sb.append(" | ").append(nowinfo.path("windScale").asText(""));
            sb.append(" | ").append(nowinfo.path("precipitation").asText("")).append("mm");
            sb.append(" | ").append(nowinfo.path("pressure").asText("")).append("hPa");
            sb.append(" |\n\n");
        }

        // 逐日预报
        sb.append("### ").append(root.path("day").asInt(1) > 1 ? root.path("day").asInt(0) + "日预报" : "7日预报").append("\n");
        sb.append("| 日期 | 白天天气 | 白天温度 | 夜间天气 | 夜间温度 | 白天风力 | 夜间风力 |\n");
        sb.append("|------|----------|----------|----------|----------|----------|----------|\n");

        // Day 1 (today)
        appendDayRow(sb, "今日", root);

        // Days 2-7
        for (int i = 2; i <= 7; i++) {
            JsonNode dayNode = root.path("weatherday" + i);
            if (dayNode.isMissingNode() || dayNode.isNull()) continue;
            String w1 = dayNode.path("weather1").asText("");
            String w2 = dayNode.path("weather2").asText("");
            if (w1.isBlank() && w2.isBlank()) continue;
            appendDayRow(sb, "第" + i + "天", dayNode);
        }
        sb.append("\n");

        // 预警信息
        JsonNode alarm = root.path("alarm");
        if (alarm.isArray() && !alarm.isEmpty()) {
            sb.append("### ⚠ 预警信息\n");
            sb.append("| 标题 | 类别 | 等级 | 生效时间 |\n");
            sb.append("|------|------|------|----------|\n");
            for (JsonNode a : alarm) {
                sb.append("| ").append(a.path("title").asText(""));
                sb.append(" | ").append(a.path("signaltype").asText(""));
                sb.append(" | ").append(a.path("signallevel").asText(""));
                sb.append(" | ").append(a.path("effective").asText(""));
                sb.append(" |\n");
            }
            sb.append("\n");
        }

        // 日出日落
        JsonNode suntimes = root.path("suntimes");
        if (suntimes.isArray() && !suntimes.isEmpty()) {
            sb.append("### 日出日落\n");
            sb.append("| 日期 | 星期 | 天亮 | 日出 | 日落 | 天黑 | 昼长 |\n");
            sb.append("|------|------|------|------|------|------|------|\n");
            for (JsonNode s : suntimes) {
                sb.append("| ").append(s.path("date").asText(""));
                sb.append(" | ").append(s.path("weekday_short_cn").asText(""));
                sb.append(" | ").append(s.path("civil_twilight_begin").asText(""));
                sb.append(" | ").append(s.path("sunrise").asText(""));
                sb.append(" | ").append(s.path("sunset").asText(""));
                sb.append(" | ").append(s.path("civil_twilight_end").asText(""));
                sb.append(" | ").append(s.path("day_length").asText(""));
                sb.append(" |\n");
            }
        }

        return sb.toString();
    }

    private void appendDayRow(StringBuilder sb, String label, JsonNode day) {
        String wd1 = day.path("wd1").asText("");
        String wd2 = day.path("wd2").asText("");
        String wind1 = day.path("winddirection1").asText("") + " " + day.path("windleve1").asText("");
        String wind2 = day.path("winddirection2").asText("") + " " + day.path("windleve2").asText("");
        sb.append("| ").append(label);
        sb.append(" | ").append(day.path("weather1").asText(""));
        sb.append(" | ").append(!wd1.isBlank() ? wd1 + "℃" : "");
        sb.append(" | ").append(day.path("weather2").asText(""));
        sb.append(" | ").append(!wd2.isBlank() ? wd2 + "℃" : "");
        sb.append(" | ").append(wind1.trim());
        sb.append(" | ").append(wind2.trim());
        sb.append(" |\n");
    }
}
