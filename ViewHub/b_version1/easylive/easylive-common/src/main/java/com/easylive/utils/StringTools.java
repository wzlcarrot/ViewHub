package com.easylive.utils;
import com.easylive.exception.BusinessException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;


import java.lang.reflect.Field;
import java.lang.reflect.Method;

//这个类的主要功能就是处理和字符串相关的操作
public class StringTools {

    public static void checkParam(Object param) {
        try {
            Field[] fields = param.getClass().getDeclaredFields();
            boolean notEmpty = false;
            for (Field field : fields) {
                String methodName = "get" + StringTools.upperCaseFirstLetter(field.getName());
                Method method = param.getClass().getMethod(methodName);
                Object object = method.invoke(param);
                if (object != null && object instanceof java.lang.String && !StringTools.isEmpty(object.toString())
                        || object != null && !(object instanceof java.lang.String)) {
                    notEmpty = true;
                    break;
                }
            }
            if (!notEmpty) {
                throw new BusinessException("多参数更新，删除，必须有非空条件");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException("校验参数是否为空失败");
        }
    }

    public static String upperCaseFirstLetter(String field) {
        if (isEmpty(field)) {
            return field;
        }
        //如果第二个字母是大写，第一个字母不大写
        if (field.length() > 1 && Character.isUpperCase(field.charAt(1))) {
            return field;
        }
        return field.substring(0, 1).toUpperCase() + field.substring(1);
    }

    public static boolean isEmpty(String str) {
        if (null == str || "".equals(str) || "null".equals(str) || "\u0000".equals(str)) {
            return true;
        } else if ("".equals(str.trim())) {
            return true;
        }
        return false;
    }

    //生成长度为count的随机字符串，存在数字和字母
    public static final String getRandomString(Integer count) {
        return RandomStringUtils.random(count, true, true);
    }

    //生成长度为count的随机数字,不存在字母
    public static final String getRandomNumber(Integer count){
        return RandomStringUtils.random(count, false, true);
    }


    //生成md5加密字符串
    public static final String encodeByMd5(String originString){
        return StringTools.isEmpty(originString) ? null: DigestUtils.md5Hex(originString);
    }

    //判断路径是否合法
    public static boolean pathIsOk(String path){
        if(StringTools.isEmpty(path)== true) return true;

        if(path.contains("../")||path.contains("..\\")){
            return false;
        }
        return true;
    }

    //获取文件后缀
    public static String getFileSuffix(String fileName){
        if(StringTools.isEmpty(fileName)|| fileName.contains(".")==false) return null;

        String suffix = fileName.substring(fileName.lastIndexOf("."));
        return suffix;
    }


}
