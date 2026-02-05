package com.easylive.entity.vo;


import com.easylive.entity.po.VideoInfo;

import java.util.List;

public class VideoInfoResultVo {
    private VideoInfo videoInfo;

    private List userActionList;

    public  VideoInfoResultVo(VideoInfo videoInfo, List userActionList) {
        this.videoInfo = videoInfo;
        this.userActionList = userActionList;
    }

    public VideoInfo getVideoInfo() {
        return videoInfo;
    }

    public void setVideoInfo(VideoInfo videoInfo) {
        this.videoInfo = videoInfo;
    }

    public List getUserActionList() {
        return userActionList;
    }

    public void setUserActionList(List userActionList) {
        this.userActionList = userActionList;
    }
}
