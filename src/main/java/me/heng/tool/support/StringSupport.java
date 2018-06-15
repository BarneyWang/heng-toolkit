package me.heng.tool.support;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static me.heng.tool.support.ListSupport.isEmpty;
import static me.heng.tool.support.ListSupport.list;

/**
 * AUTHOR: wangdi
 * DATE: 15/06/2018
 * TIME: 4:23 PM
 */
public class StringSupport {
    /**
     * 字符串join, split
     */
    private static Splitter splitter = Splitter.on(",").omitEmptyStrings();

    public static List<String> split(String str) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        return list(splitter.split(str));
    }

    public static String join(Collection<String> list) {
        if (list == null || list.size() == 0) {
            return "";
        }
        return joiner.join(list);
    }

    private static Joiner joiner = Joiner.on(",").skipNulls();

    /**
     * 按照正则分隔
     * @param str
     * @param pattern
     * @return
     */
    public static List<String> splitByPattern(String str, String pattern) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        return Splitter.onPattern(pattern).trimResults().omitEmptyStrings().splitToList(str);
    }

    private static Splitter insideSplitter = Splitter.on(",").trimResults().omitEmptyStrings();

    public static List<String> split(String str, String comma) {
        if (Strings.isNullOrEmpty(str)) {
            return Collections.emptyList();
        }
        if (",".equals(comma)) {
            return insideSplitter.splitToList(str);
        } else {
            return Splitter.on(comma).trimResults().omitEmptyStrings().splitToList(str);
        }
    }

    /**
     * 将line按照comma切分为limit个部分<br/>
     * 比如 splitWithLimit("a:b:c:de", ":", 3) => ["a", "b", "c:de"]
     *
     * @param line
     * @param comma
     * @param limit
     * @return
     */
    public static List<String> splitWithLimit(String line, String comma, int limit) {
        if (Strings.isNullOrEmpty(line) || Strings.isNullOrEmpty(comma) || limit < 1) {
            return Collections.emptyList();
        }
        int s = 0;
        int e = 0;
        List<String> vs = new ArrayList<>(limit);
        String v;
        for (int i = 1; i <= limit; i++) {
            if (i == limit) {
                v = line.substring(s);
            } else {
                e = line.indexOf(comma, s);
                if (e < 0) {
                    break;
                }
                v = line.substring(s, e);
            }
            vs.add(v);
            s = e + 1;
        }
        return vs;
    }

    public static String quote(String s) {
        return s == null ? null : "'" + s + "'";
    }

    public static String checkOrDoubleQuote(String s) {
        if (s != null && s.matches("\\s+")) {
            // 包含非空白符
            return doubleQuote(s);
        }
        return s;
    }

    public static String doubleQuote(String s) {
        return s == null ? null : "\"" + s + "\"";
    }

    public static String commonPrefix(Collection<String> items) {
        if (isEmpty(items)) {
            return "";
        }
        String[] vs = items.toArray(new String[items.size()]);
        String prefix = StringUtils.getCommonPrefix(vs);
        return prefix;
    }
}
