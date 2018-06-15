package me.heng.tool.spring;

import com.google.common.base.Throwables;
import com.google.common.io.Resources;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static me.heng.tool.support.MapSupport.flatMap;

/**
 * Created by chuanbao on 10/18/16.
 */
public class ResourceSupport {

    private static final ResourcePatternResolver RP_RESOLVER =
            new PathMatchingResourcePatternResolver();

    /**
     * 获取资源
     */
    public static Resource[] getResources(String... paths) {
        List<String> list = Arrays.asList(paths);
        List<Resource> rs = flatMap(list, path -> {
            Resource[] resources = new Resource[0];
            try {
                resources = RP_RESOLVER.getResources(path);
            } catch (IOException e) {
                Throwables.propagateIfPossible(e);
            }
            return Arrays.asList(resources);
        });
        return rs.toArray(new Resource[0]);
    }

    public static URL getResourceAsUrl(String path) {
        URL url = Resources.getResource(path);
        return url;
    }
}
