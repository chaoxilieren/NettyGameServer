package com.snowcattle.game.db.service.jdbc.test.stringEntity.onetoone;

import com.snowcattle.game.db.service.jdbc.entity.Tocken;
import com.snowcattle.game.db.service.jdbc.service.entity.impl.TockenService;
import com.snowcattle.game.db.service.jdbc.test.TestConstants;
import com.snowcattle.game.db.service.proxy.EntityProxyFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jiangwenping on 17/3/20.
 */
public class JdbcTest {
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext(new String[]{"bean/*.xml"});
        TockenService tockenService = getTockenService(classPathXmlApplicationContext);
        insertTest(classPathXmlApplicationContext, tockenService);
        insertBatchTest(classPathXmlApplicationContext, tockenService);
        Tocken tocken = getTest(classPathXmlApplicationContext, tockenService);
        List<Tocken> tockenList = getTockenList(classPathXmlApplicationContext, tockenService);
        updateTest(classPathXmlApplicationContext, tockenService, tocken);
        updateBatchTest(classPathXmlApplicationContext, tockenService, tockenList);
        deleteTest(classPathXmlApplicationContext, tockenService, tocken);
        deleteBatchTest(classPathXmlApplicationContext, tockenService, tockenList);
        getTockenList(classPathXmlApplicationContext, tockenService);
    }



    public static void deleteBatchTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService, List<Tocken> tockenList) throws Exception {
       //test2
        tockenService.deleteEntityBatch(tockenList);
    }

    public static void updateBatchTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService, List<Tocken> tockenList) throws Exception {
        EntityProxyFactory entityProxyFactory = (EntityProxyFactory) classPathXmlApplicationContext.getBean("entityProxyFactory");
        List<Tocken> updateList = new ArrayList<>();
        for (Tocken tocken : tockenList) {
            Tocken proxyTocken = entityProxyFactory.createProxyEntity(tocken);
            proxyTocken.setStatus("dddd");
            proxyTocken.setUserId(TestConstants.userId);
            proxyTocken.setId(tocken.getId());
            updateList.add(proxyTocken);
        }
        tockenService.updateEntityBatch(updateList);
    }

    public static void insertBatchTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService) throws Exception {
        int startSize = TestConstants.batchStart;
        int endSize = startSize + 10;
        List<Tocken> list = new ArrayList<>();
        for (int i = startSize; i < endSize; i++) {
            Tocken tocken = new Tocken();
            tocken.setUserId(TestConstants.userId);
            tocken.setId(TestConstants.stringId);
            tocken.setStatus("测试列表插入" + i);
            list.add(tocken);
        }

        tockenService.insertEntityBatch(list);
    }

    public static List<Tocken> getTockenList(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService) throws Exception {
        List<Tocken> tocken = tockenService.getTockenList(TestConstants.userId);
        System.out.println(tocken);
        return tocken;
    }

    public static void insertTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService) {

        int startSize = TestConstants.batchStart;
        int endSize = startSize+3;

        for (int i = startSize; i < endSize; i++) {

            Tocken tocken = new Tocken();
            tocken.setUserId(TestConstants.userId);
            tocken.setId(String.valueOf(i));
            tocken.setStatus("测试插入" + i);
            tockenService.insertTocken(tocken);
        }
    }

    public static Tocken getTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService) {
        Tocken tocken = tockenService.getTocken(TestConstants.userId, TestConstants.stringId);
        System.out.println(tocken);
        return tocken;
    }


    public static void updateTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService, Tocken tocken) throws Exception {
        EntityProxyFactory entityProxyFactory = (EntityProxyFactory) classPathXmlApplicationContext.getBean("entityProxyFactory");
        Tocken proxyTocken = entityProxyFactory.createProxyEntity(tocken);
        proxyTocken.setStatus("修改了3");
        tockenService.updateTocken(proxyTocken);

        Tocken queryTocken = tockenService.getTocken(TestConstants.userId, TestConstants.stringId);
        System.out.println(queryTocken.getStatus());
    }

    public static void deleteTest(ClassPathXmlApplicationContext classPathXmlApplicationContext, TockenService tockenService, Tocken tocken) throws Exception {
        tockenService.deleteTocken(tocken);
        Tocken queryTocken = tockenService.getTocken(TestConstants.userId, TestConstants.stringId);
        System.out.println(queryTocken);
    }

    public static TockenService getTockenService(ClassPathXmlApplicationContext classPathXmlApplicationContext) {
        TockenService tockenService = (TockenService) classPathXmlApplicationContext.getBean("tockenService");
        return tockenService;
    }
}
