package com.mcpgateway.domain;

import java.util.Map;

public class IpQueryResponse {
    private String status;
    private String t;
    private String set_cache_time;
    private Data[] data;

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }

    public String getSet_cache_time() {
        return set_cache_time;
    }

    public void setSet_cache_time(String set_cache_time) {
        this.set_cache_time = set_cache_time;
    }

    public Data[] getData() {
        return data;
    }

    public void setData(Data[] data) {
        this.data = data;
    }

    // Inner class for the data array
    public static class Data {
        private String extendedLocation;
        private String originQuery;
        private String schemaVer;
        private String appinfo;
        private int disp_type;
        private String fetchkey;
        private String location;
        private String origip;
        private String origipquery;
        private String resourceid;
        private int role_id;
        private String schemaID;
        private int shareImage;
        private int showLikeShare;
        private String showlamp;
        private Map<String, Object> strategyData;
        private String titlecont;
        private String tplt;

        // Getters and Setters
        public String getExtendedLocation() {
            return extendedLocation;
        }

        public void setExtendedLocation(String extendedLocation) {
            this.extendedLocation = extendedLocation;
        }

        public String getOriginQuery() {
            return originQuery;
        }

        public void setOriginQuery(String originQuery) {
            this.originQuery = originQuery;
        }

        public String getSchemaVer() {
            return schemaVer;
        }

        public void setSchemaVer(String schemaVer) {
            this.schemaVer = schemaVer;
        }

        public String getAppinfo() {
            return appinfo;
        }

        public void setAppinfo(String appinfo) {
            this.appinfo = appinfo;
        }

        public int getDisp_type() {
            return disp_type;
        }

        public void setDisp_type(int disp_type) {
            this.disp_type = disp_type;
        }

        public String getFetchkey() {
            return fetchkey;
        }

        public void setFetchkey(String fetchkey) {
            this.fetchkey = fetchkey;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getOrigip() {
            return origip;
        }

        public void setOrigip(String origip) {
            this.origip = origip;
        }

        public String getOrigipquery() {
            return origipquery;
        }

        public void setOrigipquery(String origipquery) {
            this.origipquery = origipquery;
        }

        public String getResourceid() {
            return resourceid;
        }

        public void setResourceid(String resourceid) {
            this.resourceid = resourceid;
        }

        public int getRole_id() {
            return role_id;
        }

        public void setRole_id(int role_id) {
            this.role_id = role_id;
        }

        public String getSchemaID() {
            return schemaID;
        }

        public void setSchemaID(String schemaID) {
            this.schemaID = schemaID;
        }

        public int getShareImage() {
            return shareImage;
        }

        public void setShareImage(int shareImage) {
            this.shareImage = shareImage;
        }

        public int getShowLikeShare() {
            return showLikeShare;
        }

        public void setShowLikeShare(int showLikeShare) {
            this.showLikeShare = showLikeShare;
        }

        public String getShowlamp() {
            return showlamp;
        }

        public void setShowlamp(String showlamp) {
            this.showlamp = showlamp;
        }

        public Map<String, Object> getStrategyData() {
            return strategyData;
        }

        public void setStrategyData(Map<String, Object> strategyData) {
            this.strategyData = strategyData;
        }

        public String getTitlecont() {
            return titlecont;
        }

        public void setTitlecont(String titlecont) {
            this.titlecont = titlecont;
        }

        public String getTplt() {
            return tplt;
        }

        public void setTplt(String tplt) {
            this.tplt = tplt;
        }
    }
}
