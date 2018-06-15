package me.heng.tool.json;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPath;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by heyong
 * <p>
 * 独立特定的Json库依赖
 */
public class JsonSupport {

    public static <T> String toJson(T data) {
        return JSON.toJSONString(data);
    }

    public static <T> T parseObject(String json, Class<T> clz) {
        if (Strings.isNullOrEmpty(json)) {
            return null;
        }
        return JSON.parseObject(json, clz);
    }

    public static Map<String, Object> parseObject(String json) {
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyMap();
        }
        return JSON.parseObject(json, JSONObject.class);
    }

    public static <T> List<T> parseArray(String json, Class<T> clz) {
        if (Strings.isNullOrEmpty(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, clz);
    }

    /**
     * 将相关从origin克隆到一个新bean
     */
    public static <T, V> V clone(T origin, Class<V> clz) {
        if (origin == null) {
            return null;
        }
        String json = JSON.toJSONString(origin);
        return JSON.parseObject(json, clz);
    }

    public static <T> Map<String,Object> cloneAsMap(T origin) {
        return clone(origin, JSONObject.class);
    }

    public static <T> Map<String,String> toStringMap(T origin) {
        Map map = clone(origin, Map.class);
        return map;
    }

    /**
     * 参见 https://github.com/alibaba/fastjson/wiki/JSONPath
     * 
     * @param json json化的字符串
     * @param path json path的语法
     * @return
     */
    public static Object getValueByPath(String json, String path) {
        if (json != null && !json.isEmpty()) {
            JSONObject obj = JSON.parseObject(json);
            return JSONPath.eval(obj, path);
        }
        return null;
    }

    public static ObjectMapper configObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
        return mapper;
    }
}
