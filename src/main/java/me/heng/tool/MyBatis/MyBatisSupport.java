package me.heng.tool.MyBatis;

import me.heng.tool.spring.ResourceSupport;
import me.heng.tool.support.ListSupport;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.util.Collection;

/**
 * Created by chuanbao on 05/12/2016.
 *
 * mybatis 一些辅助方法
 */
public class MyBatisSupport {

    /**
     * 特定加载某些 dao bean
     * @param clz
     * @param dataSource
     * @param aliasPkgs
     * @param mappers
     * @param <T>
     * @return
     * @throws Exception
     */
    public static <T> T configDaoObject(Class<T> clz, DataSource dataSource, String aliasPkgs,
            Collection<String> mappers) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setTypeAliasesPackage(aliasPkgs);
        String[] vs = ListSupport.list2vector(mappers, String.class);
        Resource[] resources = ResourceSupport.getResources(vs);
        bean.setMapperLocations(resources);
        SqlSessionFactory sessionFactory = bean.getObject();

        MapperFactoryBean factoryBean = new MapperFactoryBean();
        factoryBean.setSqlSessionFactory(sessionFactory);
        SqlSessionTemplate template = new SqlSessionTemplate(sessionFactory);
        factoryBean.setSqlSessionTemplate(template);
        factoryBean.setMapperInterface(clz);
        return (T) factoryBean.getObject();
    }
}
