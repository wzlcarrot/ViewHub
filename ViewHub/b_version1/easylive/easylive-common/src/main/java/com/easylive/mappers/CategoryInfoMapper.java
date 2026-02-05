package com.easylive.mappers;

import com.easylive.entity.po.CategoryInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分类信息 数据库操作接口
 */
public interface CategoryInfoMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据CategoryId更新
	 */
	 Integer updateByCategoryId(@Param("bean") T t,@Param("categoryId") Integer categoryId);


	/**
	 * 根据CategoryId删除
	 */
	 Integer deleteByCategoryId(@Param("categoryId") Integer categoryId);


	/**
	 * 根据CategoryId获取对象
	 */
	 T selectByCategoryId(@Param("categoryId") Integer categoryId);


	/**
	 * 根据CategoryCode更新
	 */
	 Integer updateByCategoryCode(@Param("bean") T t,@Param("categoryCode") String categoryCode);


	/**
	 * 根据CategoryCode删除
	 */
	 Integer deleteByCategoryCode(@Param("categoryCode") String categoryCode);


	/**
	 * 根据CategoryCode获取对象
	 */
	 T selectByCategoryCode(@Param("categoryCode") String categoryCode);


	 //根据给定的父分类 ID 查询其下子分类的最大排序值。方便后面添加子类id+1.更加符合逻辑
	 //@Param("pCategoryId")链接sql语句的参数。
	 Integer selectMaxSort(@Param("pCategoryId") Integer pCategoryId);

	 void updateSortBatch(@Param("categoryInfoList") List<CategoryInfo> categoryInfoList);
}
