package com.easylive.entity.vo;

import com.easylive.entity.po.VideoInfoFilePost;
import com.easylive.entity.po.VideoInfoPost;

import java.util.List;

public class VideoPostEditInfoVo {
    private VideoInfoPost videoInfo;   //视频提交信息
    private List<VideoInfoFilePost> videoInfoFileList;   //视频分批信息

    public VideoInfoPost getVideoInfo() {
        return videoInfo;
    }

    public void setVideoInfo(VideoInfoPost videoInfo) {
        this.videoInfo = videoInfo;
    }

    public List<VideoInfoFilePost> getVideoInfoFileList() {
        return videoInfoFileList;
    }

    public void setVideoInfoFileList(List<VideoInfoFilePost> videoInfoFileList) {
        this.videoInfoFileList = videoInfoFileList;
    }
}
