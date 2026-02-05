package com.easylive.web.controller;

import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.enums.StatisticsTypeEnum;
import com.easylive.entity.po.StatisticsInfo;
import com.easylive.entity.query.StatisticsInfoQuery;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.service.StatisticsInfoService;
import com.easylive.utils.DateUtil;
import com.easylive.web.annotation.GlobalInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ucenter")
@RequiredArgsConstructor
public class UcenterStatisticsController extends ABaseController{

    private final StatisticsInfoService statisticsInfoService;

    @RequestMapping("/getActualTimeStatisticsInfo")
    @GlobalInterceptor(checkLogin = true)
    public ResponseVO getActualTimeStatisticsInfo() {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        String preDate = DateUtil.getYesterday();

        StatisticsInfoQuery query = new StatisticsInfoQuery();
        query.setStatisticsDate(preDate);
        query.setUserId(tokenUserInfoDto.getUserId());

        //啊啊啊。我知道了，，，增长量的体现就是通过同一天行数据的增加
        List<StatisticsInfo> preDayData = statisticsInfoService.findListByParam(query);  //获取指定用户的昨天的统计数据，就是昨天的增长量

        System.out.println("preDayData:"+preDayData);

        //键是数据类型，比如播放量，粉丝数， 点赞数等。。值就是对应的数据值
        Map<Integer, Integer> preDayDataMap = new HashMap<>();
        for (StatisticsInfo item : preDayData) {
            System.out.println("item:"+item.getDataType()+" "+"item:"+item.getStatisticsCount());
            preDayDataMap.put(item.getDataType(), item.getStatisticsCount());
        }

        Map<String, Integer> totalCountInfo = statisticsInfoService.getStatisticsInfoActualTime(tokenUserInfoDto.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("preDayData", preDayDataMap);
        result.put("totalCountInfo", totalCountInfo);

        return getSuccessResponseVO(result);
    }


    @RequestMapping("/getWeekStatisticsInfo")
    @GlobalInterceptor
    public ResponseVO getWeekStatisticsInfo(Integer dataType) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        List<String> dateList = DateUtil.getBeforeDates(7);

        StatisticsInfoQuery param = new StatisticsInfoQuery();
        param.setDataType(dataType);
        param.setStatisticsDateStart(dateList.get(0));
        param.setStatisticsDateEnd(dateList.get(dateList.size() - 1));
        param.setOrderBy("statistics_date asc");
        param.setUserId(tokenUserInfoDto.getUserId());
        List<StatisticsInfo> statisticsInfoList = statisticsInfoService.findListByParam(param);  //指定特定用户的所有数据查询
        //key是日期，value是数据。。value本身有日期的
        Map<String, StatisticsInfo> dataMap = new HashMap<>();

        for (StatisticsInfo item : statisticsInfoList) {
            String dateKey = item.getStatisticsDate();  // 用日期作为key
            dataMap.put(dateKey, item);  // 把整条记录放进去
        }
        //存放一个礼拜每天的数据
        List<StatisticsInfo> resultDataList = new ArrayList<>();
        for (String date : dateList) {
            StatisticsInfo dataItem = dataMap.get(date);
            //可能有某天的数据不存在，，则需要初始化
            if (dataItem == null) {
                dataItem = new StatisticsInfo();
                dataItem.setStatisticsCount(0);
                dataItem.setStatisticsDate(date);
            }
            resultDataList.add(dataItem);
        }

        return getSuccessResponseVO(resultDataList);
    }

}
