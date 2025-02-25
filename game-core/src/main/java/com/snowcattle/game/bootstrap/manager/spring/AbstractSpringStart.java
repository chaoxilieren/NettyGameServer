package com.snowcattle.game.bootstrap.manager.spring;

import com.snowcattle.game.common.constant.Loggers;
import com.snowcattle.game.service.IService;
import org.slf4j.Logger;

import java.lang.reflect.Field;

/**
 * Created by jiangwenping on 17/4/5.
 * 抽象的spring启动
 */
public abstract class AbstractSpringStart {

    private static final Logger logger = Loggers.serverLogger;

    public void start() throws Exception {
        // 获取对象obj的所有属性域
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            // 对于每个属性，获取属性名
            String varName = field.getName();
            try {
                // 修改反射访问检查方式
                field.setAccessible(true);
                
                // 从obj中获取field变量
                Object object = field.get(this);
                if (object instanceof IService) {
                    IService iService = (IService) object;
                    iService.startup();
                    logger.info(iService.getId() + " service start up");
                } else if (object != null) {
                    logger.info(object.getClass().getSimpleName() + " start up");
                }
                
                field.setAccessible(false);
                
            } catch (Exception ex) {
                logger.error("Failed to start service: " + varName, ex);
                throw ex;
            }
        }
    }

    public void stop() throws Exception {
        // 获取对象obj的所有属性域
        Field[] fields = this.getClass().getDeclaredFields();

        for (Field field : fields) {
            // 对于每个属性，获取属性名
            String varName = field.getName();
            try {
                // 修改反射访问检查方式
                field.setAccessible(true);

                // 从obj中获取field变量
                Object object = field.get(this);
                if (object instanceof IService) {
                    IService iService = (IService) object;
                    iService.shutdown();
                    logger.info(iService.getId() + " shut down");
                } else if (object != null) {
                    logger.info(object.getClass().getSimpleName() + " shut down");
                }
                
                field.setAccessible(false);
                
            } catch (Exception ex) {
                logger.error("Failed to stop service: " + varName, ex);
                throw ex;
            }
        }
    }
}
