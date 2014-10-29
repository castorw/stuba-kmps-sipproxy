package net.ctrdn.talk.portal.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ctrdn.talk.exception.ApiMethodException;
import net.ctrdn.talk.exception.ApiRegistryException;
import net.ctrdn.talk.exception.InitializationException;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiMethodRegistry {

    private final Logger logger = LoggerFactory.getLogger(ApiMethodRegistry.class);
    private static ApiMethodRegistry registry;
    private final Map<String, Class<? extends ApiMethod>> methodClassMap = new ConcurrentHashMap<>();

    private ApiMethodRegistry() throws InitializationException {
        Reflections reflections = new Reflections("net.ctrdn");
        try {
            for (Class<?> foundClass : reflections.getSubTypesOf(ApiMethod.class)) {
                if (!Modifier.isAbstract(foundClass.getModifiers())) {
                    Constructor constructor = foundClass.getConstructor();
                    ApiMethod instance = (ApiMethod) constructor.newInstance();
                    String path = instance.getPath();
                    methodClassMap.put(path, (Class<? extends ApiMethod>) foundClass);
                    this.logger.trace("Adding API method " + path);
                }
            }
            this.logger.debug("Populated " + this.methodClassMap.size() + " API methods");
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            throw new InitializationException("Error populating API registry", ex);
        }
    }

    public ApiMethod getMethod(String path) throws ApiRegistryException {
        if (this.methodClassMap.containsKey(path)) {
            try {
                Class<? extends ApiMethod> clz = this.methodClassMap.get(path);
                Constructor ctr = clz.getConstructor();
                ApiMethod inst = (ApiMethod) ctr.newInstance();
                return inst;
            } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
                throw new ApiRegistryException("Unable to instantinate API method", ex);
            }
        }
        return null;
    }

    public static void initalize() throws InitializationException {
        ApiMethodRegistry.registry = new ApiMethodRegistry();
    }

    public static ApiMethodRegistry getInstance() {
        return ApiMethodRegistry.registry;
    }
}
