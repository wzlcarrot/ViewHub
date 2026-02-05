package com.easylive.admin.controller;

import com.easylive.entity.po.CategoryInfo;
import com.easylive.entity.query.CategoryInfoQuery;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.CategoryInfoService;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController extends ABaseController{

    private final CategoryInfoService categoryInfoService;

    @RequestMapping("/loadCategory")
    //加载分类数据
    public ResponseVO loadDataList(CategoryInfoQuery query) {
        query.setOrderBy("sort asc");
        query.setConvertTree(true);


        List<CategoryInfo> categoryInfoList = categoryInfoService.findListByParam(query);

        return getSuccessResponseVO(categoryInfoList);
    }

    @RequestMapping("/saveCategory")
    //保存分类数据
    public ResponseVO saveCategory(@NotNull  Integer pCategoryId,
                                   Integer categoryId,
                                   @NotEmpty String categoryCode,
                                   @NotEmpty String categoryName,
                                   String icon,
                                   String background) {

        CategoryInfo categoryInfo = new CategoryInfo();
        categoryInfo.setCategoryId(categoryId);
        categoryInfo.setCategoryCode(categoryCode);
        categoryInfo.setCategoryName(categoryName);
        categoryInfo.setIcon(icon);
        categoryInfo.setpCategoryId(pCategoryId);
        categoryInfo.setBackground(background);
        categoryInfoService.saveCategoryInfo(categoryInfo);
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/delCategory")
    //删除指定id分类数据
    public ResponseVO deleteCategory(@NotNull Integer categoryId) {

        categoryInfoService.deleteCategory(categoryId);

        return getSuccessResponseVO(null);
    }

    //通过调整前端页面的分类id和分类id数组，来进行分类排序
    @RequestMapping("/changeSort")
    public ResponseVO changeSort(@NotNull Integer pCategoryId, @NotEmpty String categoryIds){

        categoryInfoService.changeSort(pCategoryId, categoryIds);

        return getSuccessResponseVO(null);
    }

}
