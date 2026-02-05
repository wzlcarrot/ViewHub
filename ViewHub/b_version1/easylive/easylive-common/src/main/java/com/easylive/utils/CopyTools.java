package com.easylive.utils;

import org.springframework.beans.BeanUtils;

import java.util.*;

//拷贝工具类
public class CopyTools {
    public static <T, S> List<T> copyList(List<S> sList, Class<T> classz) {
        List<T> list = new ArrayList<T>();
        for (S s : sList) {
            T t = null;
            try {
                t = classz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            BeanUtils.copyProperties(s, t);
            list.add(t);
        }
        return list;
    }

    public static <T, S> T copy(S s, Class<T> classz) {
        T t = null;
        try {
            t = classz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        BeanUtils.copyProperties(s, t);
        return t;
    }

    //该类的核心方法，将s的属性复制给t
    public static <T, S> void copyProperties(S s, T t) {
        BeanUtils.copyProperties(s, t);
    }

}