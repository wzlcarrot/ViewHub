package com.easylive.service.impl;

import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.easylive.cache.ThreeLevelCacheManager;
import com.easylive.component.RedisComponent;
import com.easylive.entity.constant.Constants;
import com.easylive.redis.BloomFilterComponent;
import lombok.extern.slf4j.Slf4j;
import com.easylive.entity.query.VideoInfoQuery;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.service.VideoInfoService;
import org.springframework.stereotype.Service;

import com.easylive.entity.enums.PageSize;
import com.easylive.entity.query.CategoryInfoQuery;
import com.easylive.entity.po.CategoryInfo;
import com.easylive.entity.vo.PaginationResultVO;
import com.easylive.entity.query.SimplePage;
import com.easylive.mappers.CategoryInfoMapper;
import com.easylive.service.CategoryInfoService;
import com.easylive.utils.StringTools;
import org.springframework.web.bind.annotation.RequestMapping;


/**
 * 分类信息 业务接口实现
 */
@Service("categoryInfoService")
@Slf4j
@RequiredArgsConstructor
public class CategoryInfoServiceImpl implements CategoryInfoService {

	private final CategoryInfoMapper<CategoryInfo, CategoryInfoQuery> categoryInfoMapper;

	private final RedisComponent redisComponent;

	private final VideoInfoService videoInfoService;

	private final ThreeLevelCacheManager threeLevelCacheManager;

	private final BloomFilterComponent bloomFilterComponent;

	/**
	 * 分类信息缓存键前缀
	 */
	private static final String CACHE_KEY_PREFIX_CATEGORY = "category_info:";

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<CategoryInfo> findListByParam(CategoryInfoQuery param) {
		List<CategoryInfo> categoryInfoList = categoryInfoMapper.selectList(param);

		if(param.getConvertTree()!=null&&param.getConvertTree()){
			categoryInfoList = convertLine2Tree(categoryInfoList, Constants.ZERO);
		}

		return categoryInfoList;
	}

	//得到的返回值是pid的直接子类
	private List<CategoryInfo> convertLine2Tree(List<CategoryInfo> dataList,Integer pid){
		List<CategoryInfo> children = new ArrayList<>();

		for(CategoryInfo m:dataList){
			//意思就是：如果当前节点的父节点id等于pid，则将当前节点添加到children中
			if(m.getCategoryId()!=null&&m.getpCategoryId()!=null&&m.getpCategoryId().equals(pid)){
				m.setChildren(convertLine2Tree(dataList,m.getCategoryId()));
				children.add(m);
			}
		}

		return children;
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(CategoryInfoQuery param) {
		return this.categoryInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<CategoryInfo> findListByPage(CategoryInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<CategoryInfo> list = this.findListByParam(param);
		PaginationResultVO<CategoryInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(CategoryInfo bean) {
		return this.categoryInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<CategoryInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.categoryInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<CategoryInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.categoryInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(CategoryInfo bean, CategoryInfoQuery param) {
		StringTools.checkParam(param);
		return this.categoryInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(CategoryInfoQuery param) {
		StringTools.checkParam(param);
		return this.categoryInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据CategoryId获取对象（使用布隆过滤器+三级缓存）
	 * 场景：分类信息变化少，查询频繁，非常适合缓存
	 */
	@Override
	public CategoryInfo getCategoryInfoByCategoryId(Integer categoryId) {
		// 1. 布隆过滤器判断
		if (!bloomFilterComponent.mightContainCategory(categoryId)) {
			log.debug("布隆过滤器判断分类不存在, categoryId={}", categoryId);
			return null;
		}

		// 2. 三级缓存查询
		String cacheKey = CACHE_KEY_PREFIX_CATEGORY + categoryId;
		CategoryInfo categoryInfo = threeLevelCacheManager.get(cacheKey, key -> {
			log.info("从MySQL加载分类信息，categoryId: {}", categoryId);
			return this.categoryInfoMapper.selectByCategoryId(categoryId);
		});

		// 3. 无论是否存在，都加入布隆过滤器（防止重复穿透）
		bloomFilterComponent.addCategory(categoryId);

		return categoryInfo;
	}

	/**
	 * 根据CategoryId修改（更新时删除缓存）
	 */
	@Override
	public Integer updateCategoryInfoByCategoryId(CategoryInfo bean, Integer categoryId) {
		Integer result = this.categoryInfoMapper.updateByCategoryId(bean, categoryId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_CATEGORY + categoryId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("更新分类信息，删除缓存，categoryId: {}", categoryId);
		}
		return result;
	}

	/**
	 * 根据CategoryId删除（删除时删除缓存）
	 */
	@Override
	public Integer deleteCategoryInfoByCategoryId(Integer categoryId) {
		Integer result = this.categoryInfoMapper.deleteByCategoryId(categoryId);
		if (result > 0) {
			String cacheKey = CACHE_KEY_PREFIX_CATEGORY + categoryId;
			threeLevelCacheManager.evict(cacheKey);
			log.info("删除分类信息，删除缓存，categoryId: {}", categoryId);
		}
		return result;
	}

	/**
	 * 根据CategoryCode获取对象
	 */
	@Override
	public CategoryInfo getCategoryInfoByCategoryCode(String categoryCode) {
		return this.categoryInfoMapper.selectByCategoryCode(categoryCode);
	}

	/**
	 * 根据CategoryCode修改
	 */
	@Override
	public Integer updateCategoryInfoByCategoryCode(CategoryInfo bean, String categoryCode) {
		return this.categoryInfoMapper.updateByCategoryCode(bean, categoryCode);
	}

	/**
	 * 根据CategoryCode删除
	 */
	@Override
	public Integer deleteCategoryInfoByCategoryCode(String categoryCode) {
		return this.categoryInfoMapper.deleteByCategoryCode(categoryCode);
	}

	//把相关信息存到数据库
	@Override
	public void saveCategoryInfo(CategoryInfo bean) {
		//通过分类编号查询相关信息
		CategoryInfo dbBean = categoryInfoMapper.selectByCategoryCode(bean.getCategoryCode());

		//如果这个分类编号对应的数据不存在，则进行insert操作。
		if ((bean.getCategoryId() == null && dbBean != null) ||
				(bean.getCategoryId() != null && dbBean != null && !dbBean.getCategoryId().equals(bean.getCategoryId()))) {
			throw new BusinessException("分类编号已经存在");
		}

		if(bean.getCategoryId()==null){
			Integer maxSort = categoryInfoMapper.selectMaxSort(bean.getpCategoryId());
			if(maxSort==null) maxSort = 0;
			bean.setSort(maxSort+1);

			categoryInfoMapper.insert(bean);
			// 新增分类后，加入布隆过滤器
			bloomFilterComponent.addCategory(bean.getCategoryId());
			log.debug("新增分类，加入布隆过滤器，categoryId={}", bean.getCategoryId());
		}
		else{
			categoryInfoMapper.updateByCategoryId(bean,bean.getCategoryId());
			// 更新分类信息后，删除三级缓存
			String cacheKey = CACHE_KEY_PREFIX_CATEGORY + bean.getCategoryId();
			threeLevelCacheManager.evict(cacheKey);
			log.info("保存分类信息，删除缓存，categoryId: {}", bean.getCategoryId());
			// 确保分类在布隆过滤器中
			bloomFilterComponent.addCategory(bean.getCategoryId());
		}

		save2Redis();
	}


	//删除分类信息
	public void deleteCategory(Integer categoryId) {
		//先看看分类下是否有视频，如果有视频则不允许删除分类。
		VideoInfoQuery videoInfoQuery = new VideoInfoQuery();
		videoInfoQuery.setCategoryIdOrPCategoryId(categoryId);
		Integer count = videoInfoService.findCountByParam(videoInfoQuery);

		if(count>0){
			throw new BusinessException("该分类下有视频，不允许删除");
		}


		CategoryInfoQuery categoryInfoQuery = new CategoryInfoQuery();
		categoryInfoQuery.setCategoryIdOrPCategoryId(categoryId);
		categoryInfoMapper.deleteByParam(categoryInfoQuery);

		// 删除分类后，删除三级缓存
		String cacheKey = CACHE_KEY_PREFIX_CATEGORY + categoryId;
		threeLevelCacheManager.evict(cacheKey);
		log.info("删除分类，删除缓存，categoryId: {}", categoryId);

		save2Redis();
	}

	//调整排序
	@Override
	public void changeSort(Integer pCategoryId, String categoryIds) {
		String [] categoryIdArray = categoryIds.split(",");
		List<CategoryInfo> categoryInfoList = new ArrayList<CategoryInfo>();
		Integer sort = 0;
		for(String categoryId : categoryIdArray){
			CategoryInfo categoryInfo = new CategoryInfo();
			categoryInfo.setCategoryId(Integer.parseInt(categoryId));
			categoryInfo.setpCategoryId(pCategoryId);
			categoryInfo.setSort(++sort);
			categoryInfoList.add(categoryInfo);
		}

		categoryInfoMapper.updateSortBatch(categoryInfoList);

		//刷新缓存
		save2Redis();
	}

	//把分类列表保存到redis中
	private void save2Redis(){
		CategoryInfoQuery query = new CategoryInfoQuery();
		query.setOrderBy("sort asc");
		query.setConvertTree(true);
		//查询所有分类
		List<CategoryInfo> categoryInfoList = findListByParam(query);

		redisComponent.saveCategoryList(categoryInfoList);
	}


	//直接从redis缓存中拿取所有分类信息
	@Override
	public List<CategoryInfo> getAllCategoryList() {
		List<CategoryInfo> categoryInfoList = redisComponent.getCategoryList();
		if(categoryInfoList.size()==0){
			save2Redis();
		}

		return redisComponent.getCategoryList();
	}


}